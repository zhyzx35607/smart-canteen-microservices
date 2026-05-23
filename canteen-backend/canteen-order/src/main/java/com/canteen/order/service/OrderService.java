package com.canteen.order.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.ResultCode;
import com.canteen.order.dto.*;
import com.canteen.order.entity.Order;
import com.canteen.order.entity.OrderItem;
import com.canteen.order.feign.MenuDishClient;
import com.canteen.order.feign.MenuMerchantClient;
import com.canteen.order.feign.MenuServiceClient;
import com.canteen.order.feign.PickupServiceClient;
import com.canteen.order.config.RocketMQConfig;
import com.canteen.order.mapper.OrderItemMapper;
import com.canteen.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final MenuDishClient menuDishClient;
    private final MenuMerchantClient menuMerchantClient;
    private final MenuServiceClient menuServiceClient;
    private final PickupServiceClient pickupServiceClient;
    private final OrderStateMachine orderStateMachine;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQConfig rocketMQConfig;

    private static final String IDEMPOTENT_PREFIX = "order:idem:";
    private static final String PICKUP_CODE_PREFIX = "pickup:code:seq:";

    @Transactional
    public OrderVO placeOrder(Long userId, PlaceOrderRequest req, String idempotencyKey) {
        // 幂等校验
        if (idempotencyKey != null) {
            Boolean setSuccess = stringRedisTemplate.opsForValue()
                    .setIfAbsent(IDEMPOTENT_PREFIX + idempotencyKey, "1", 60, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(setSuccess)) {
                throw new BusinessException(ResultCode.ORDER_IDEMPOTENT_DUPLICATE);
            }
        }

        // 计算总价 & 校验菜品
        int totalCents = 0;
        List<OrderItem> orderItems = new ArrayList<>();
        List<StockDeductRequest.StockItem> stockItems = new ArrayList<>();

        for (PlaceOrderRequest.OrderItemDTO itemDTO : req.getItems()) {
            var dishResult = menuDishClient.getDish(itemDTO.getDishId());
            if (dishResult == null || dishResult.getData() == null) {
                throw new BusinessException(ResultCode.MENU_DISH_NOT_FOUND);
            }
            MenuDishClient.DishInfo dish = dishResult.getData();
            if (dish.getOnShelf() != 1) {
                throw new BusinessException(ResultCode.MENU_DISH_OFF_SHELF);
            }

            OrderItem oi = new OrderItem();
            oi.setDishId(dish.getId());
            oi.setDishNameSnapshot(dish.getName());
            oi.setUnitPrice(dish.getPriceCents());
            oi.setQuantity(itemDTO.getQuantity());
            orderItems.add(oi);

            totalCents += dish.getPriceCents() * itemDTO.getQuantity();

            stockItems.add(new StockDeductRequest.StockItem(dish.getId(), itemDTO.getQuantity()));
        }

        // 扣减库存（InternalTokenInterceptor 自动注入 X-Internal-Token）
        StockDeductRequest deductReq = new StockDeductRequest();
        deductReq.setItems(stockItems);
        var deductResult = menuServiceClient.deduct(deductReq);
        if (deductResult == null || !Boolean.TRUE.equals(deductResult.getData())) {
            throw new BusinessException(ResultCode.MENU_STOCK_INSUFFICIENT);
        }

        // 查询商户取餐窗口
        var merchantResult = menuMerchantClient.getMerchant(req.getMerchantId());
        if (merchantResult == null || merchantResult.getData() == null) {
            throw new BusinessException(ResultCode.MENU_MERCHANT_NOT_FOUND);
        }
        String counterId = merchantResult.getData().getCounterId();

        // 写订单
        Order order = new Order();
        order.setUserId(userId);
        order.setMerchantId(req.getMerchantId());
        order.setCounterId(counterId);
        order.setStatus(Order.STATUS_PLACED);
        order.setTotalCents(totalCents);
        order.setPlacedAt(LocalDateTime.now());
        orderMapper.insert(order);

        // 写订单明细
        for (OrderItem oi : orderItems) {
            oi.setOrderId(order.getId());
            orderItemMapper.insert(oi);
        }

        // 发送 RocketMQ 延时消息 order.timeout（30min 后消费）
        rocketMQConfig.sendOrderTimeoutMessage(order.getId());

        log.info("Order placed: id={}, userId={}, total={}", order.getId(), userId, totalCents);
        return toVO(order, orderItems);
    }

    @Transactional
    public OrderVO acceptOrder(Long orderId) {
        Order order = getOrderOrThrow(orderId);
        String newStatus = orderStateMachine.transition(order.getStatus(), "accept");
        order.setStatus(newStatus);
        order.setAcceptedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        log.info("Order accepted: id={}", orderId);
        return toVO(order);
    }

    @Transactional
    public OrderVO startPreparing(Long orderId) {
        Order order = getOrderOrThrow(orderId);
        String newStatus = orderStateMachine.transition(order.getStatus(), "start");
        order.setStatus(newStatus);
        orderMapper.updateById(order);

        log.info("Order started preparing: id={}", orderId);
        return toVO(order);
    }

    @Transactional
    public OrderVO readyForPickup(Long orderId) {
        Order order = getOrderOrThrow(orderId);
        String newStatus = orderStateMachine.transition(order.getStatus(), "ready");
        order.setStatus(newStatus);
        order.setReadyAt(LocalDateTime.now());

        // 生成取餐码
        String pickupCode = generatePickupCode(order.getCounterId());
        order.setPickupCode(pickupCode);
        orderMapper.updateById(order);

        // 通知取餐服务入队（InternalTokenInterceptor 自动注入 token）
        PickupEnqueueRequest enqueueReq = new PickupEnqueueRequest(
                order.getId(), pickupCode, order.getUserId(), order.getCounterId());
        pickupServiceClient.enqueue(enqueueReq);

        log.info("Order ready for pickup: id={}, pickupCode={}", orderId, pickupCode);
        return toVO(order);
    }

    @Transactional
    public OrderVO cancelOrder(Long orderId, String reason) {
        Order order = getOrderOrThrow(orderId);
        String newStatus = orderStateMachine.transition(order.getStatus(), "cancel");
        order.setStatus(newStatus);
        order.setCanceledAt(LocalDateTime.now());
        order.setCancelReason(reason);
        orderMapper.updateById(order);

        // 回滚库存
        rollbackStock(orderId);

        log.info("Order canceled: id={}, reason={}", orderId, reason);
        return toVO(order);
    }

    @Transactional
    public void markPickedUp(Long orderId) {
        Order order = getOrderOrThrow(orderId);
        String newStatus = orderStateMachine.transition(order.getStatus(), "pickup");
        order.setStatus(newStatus);
        order.setPickedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("Order picked up: id={}", orderId);
    }

    /**
     * 超时取消（由延时消息触发）
     */
    @Transactional
    public void timeoutCancel(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) return;

        if (Order.STATUS_PLACED.equals(order.getStatus()) || Order.STATUS_ACCEPTED.equals(order.getStatus())) {
            order.setStatus(Order.STATUS_CANCELED);
            order.setCanceledAt(LocalDateTime.now());
            order.setCancelReason("超时自动取消");
            orderMapper.updateById(order);

            rollbackStock(orderId);
            log.info("Order timeout canceled: id={}", orderId);
        }
    }

    public OrderVO getOrderDetail(Long orderId) {
        Order order = getOrderOrThrow(orderId);
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        return toVO(order, items);
    }

    public Page<OrderVO> listOrders(Long userId, int page, int size) {
        Page<Order> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(Order::getUserId, userId);
        }
        wrapper.orderByDesc(Order::getCreatedAt);

        Page<Order> result = orderMapper.selectPage(pageParam, wrapper);
        Page<OrderVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    private void rollbackStock(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        List<StockDeductRequest.StockItem> stockItems = items.stream()
                .map(oi -> new StockDeductRequest.StockItem(oi.getDishId(), oi.getQuantity()))
                .toList();
        StockDeductRequest restoreReq = new StockDeductRequest();
        restoreReq.setItems(stockItems);
        menuServiceClient.restore(restoreReq);
    }

    private Order getOrderOrThrow(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private String generatePickupCode(String counterId) {
        Long seq = stringRedisTemplate.opsForValue().increment(PICKUP_CODE_PREFIX + counterId);
        return String.format("%s%06d", counterId, seq % 1000000);
    }

    private OrderVO toVO(Order order) {
        return toVO(order, null);
    }

    private OrderVO toVO(Order order, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        BeanUtil.copyProperties(order, vo);
        if (items != null) {
            vo.setItems(items.stream().map(oi -> {
                OrderVO.OrderItemVO itemVO = new OrderVO.OrderItemVO();
                BeanUtil.copyProperties(oi, itemVO);
                return itemVO;
            }).toList());
        }
        return vo;
    }
}
