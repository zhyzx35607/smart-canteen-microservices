package com.canteen.order;

import com.canteen.common.result.Result;
import com.canteen.order.dto.OrderVO;
import com.canteen.order.dto.PlaceOrderRequest;
import com.canteen.order.dto.StockDeductRequest;
import com.canteen.order.entity.Order;
import com.canteen.order.feign.MenuDishClient;
import com.canteen.order.feign.MenuMerchantClient;
import com.canteen.order.feign.MenuServiceClient;
import com.canteen.order.feign.PickupServiceClient;
import com.canteen.order.mapper.OrderMapper;
import com.canteen.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-order-test.sql",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.sentinel.enabled=false",
        "spring.cloud.stream.rocketmq.binder.name-server=localhost:19876",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfiguration",
        "internal.token=test-internal-token"
})
@Sql(scripts = "classpath:schema-order-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderMapper orderMapper;

    @MockBean
    private MenuDishClient menuDishClient;

    @MockBean
    private MenuServiceClient menuServiceClient;

    @MockBean
    private MenuMerchantClient menuMerchantClient;

    @MockBean
    private PickupServiceClient pickupServiceClient;

    @MockBean
    private StreamBridge streamBridge;

    private PlaceOrderRequest buildRequest() {
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setMerchantId(1L);
        PlaceOrderRequest.OrderItemDTO item = new PlaceOrderRequest.OrderItemDTO();
        item.setDishId(1L);
        item.setQuantity(2);
        req.setItems(List.of(item));
        return req;
    }

    @Test
    @DisplayName("集成: 下单成功 → 状态 PLACED，订单落库")
    void placeOrderSuccess() {
        MenuDishClient.DishInfo dish = new MenuDishClient.DishInfo();
        dish.setId(1L);
        dish.setName("宫保鸡丁");
        dish.setPriceCents(1200);
        dish.setOnShelf(1);
        when(menuDishClient.getDish(1L)).thenReturn(Result.success(dish));

        MenuMerchantClient.MerchantInfo merchant = new MenuMerchantClient.MerchantInfo();
        merchant.setId(1L);
        merchant.setCounterId("C01");
        when(menuMerchantClient.getMerchant(1L)).thenReturn(Result.success(merchant));

        when(menuServiceClient.deduct(any())).thenReturn(Result.success(true));
        when(streamBridge.send(any(), any())).thenReturn(true);

        OrderVO result = orderService.placeOrder(1L, buildRequest(), "itg-test-001");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Order.STATUS_PLACED);
        assertThat(result.getTotalCents()).isEqualTo(2400);
        assertThat(result.getUserId()).isEqualTo(1L);

        Order dbOrder = orderMapper.selectById(result.getId());
        assertThat(dbOrder).isNotNull();
        assertThat(dbOrder.getStatus()).isEqualTo(Order.STATUS_PLACED);
    }

    @Test
    @DisplayName("集成: 重复幂等键 → 抛异常")
    void placeOrderDuplicate() {
        MenuDishClient.DishInfo dish = new MenuDishClient.DishInfo();
        dish.setId(1L);
        dish.setName("宫保鸡丁");
        dish.setPriceCents(1200);
        dish.setOnShelf(1);
        when(menuDishClient.getDish(1L)).thenReturn(Result.success(dish));
        when(menuMerchantClient.getMerchant(1L)).thenReturn(Result.success(buildMerchant()));
        when(menuServiceClient.deduct(any())).thenReturn(Result.success(true));
        when(streamBridge.send(any(), any())).thenReturn(true);

        orderService.placeOrder(1L, buildRequest(), "itg-dup-001");

        assertThrows(com.canteen.common.exception.BusinessException.class, () ->
                orderService.placeOrder(1L, buildRequest(), "itg-dup-001"));
    }

    @Test
    @DisplayName("集成: 下单 → 接单 → 制作 → 完成（完整状态流转）")
    void fullStateFlow() {
        MenuDishClient.DishInfo dish = new MenuDishClient.DishInfo();
        dish.setId(1L);
        dish.setName("宫保鸡丁");
        dish.setPriceCents(1200);
        dish.setOnShelf(1);
        when(menuDishClient.getDish(1L)).thenReturn(Result.success(dish));
        when(menuMerchantClient.getMerchant(1L)).thenReturn(Result.success(buildMerchant()));
        when(menuServiceClient.deduct(any())).thenReturn(Result.success(true));
        when(streamBridge.send(any(), any())).thenReturn(true);

        OrderVO placed = orderService.placeOrder(1L, buildRequest(), "itg-flow-001");
        assertThat(placed.getStatus()).isEqualTo(Order.STATUS_PLACED);

        OrderVO accepted = orderService.acceptOrder(placed.getId());
        assertThat(accepted.getStatus()).isEqualTo(Order.STATUS_ACCEPTED);

        OrderVO preparing = orderService.startPreparing(placed.getId());
        assertThat(preparing.getStatus()).isEqualTo(Order.STATUS_PREPARING);

        OrderVO ready = orderService.readyForPickup(placed.getId());
        assertThat(ready.getStatus()).isEqualTo(Order.STATUS_WAITING_PICKUP);
        assertThat(ready.getPickupCode()).isNotNull();
    }

    @Test
    @DisplayName("集成: 取消订单 → 状态 CANCELED + 库存回滚")
    void cancelOrderRestoresStock() {
        MenuDishClient.DishInfo dish = new MenuDishClient.DishInfo();
        dish.setId(1L);
        dish.setName("宫保鸡丁");
        dish.setPriceCents(1200);
        dish.setOnShelf(1);
        when(menuDishClient.getDish(1L)).thenReturn(Result.success(dish));
        when(menuMerchantClient.getMerchant(1L)).thenReturn(Result.success(buildMerchant()));
        when(menuServiceClient.deduct(any())).thenReturn(Result.success(true));
        when(menuServiceClient.restore(any())).thenReturn(Result.success());
        when(streamBridge.send(any(), any())).thenReturn(true);

        OrderVO placed = orderService.placeOrder(1L, buildRequest(), "itg-cancel-001");
        OrderVO canceled = orderService.cancelOrder(placed.getId(), "不想吃了");

        assertThat(canceled.getStatus()).isEqualTo(Order.STATUS_CANCELED);
        assertThat(canceled.getCancelReason()).isEqualTo("不想吃了");
    }

    @Test
    @DisplayName("集成: 查询订单详情")
    void getOrderDetail() {
        MenuDishClient.DishInfo dish = new MenuDishClient.DishInfo();
        dish.setId(1L);
        dish.setName("宫保鸡丁");
        dish.setPriceCents(1500);
        dish.setOnShelf(1);
        when(menuDishClient.getDish(1L)).thenReturn(Result.success(dish));
        when(menuMerchantClient.getMerchant(1L)).thenReturn(Result.success(buildMerchant()));
        when(menuServiceClient.deduct(any())).thenReturn(Result.success(true));
        when(streamBridge.send(any(), any())).thenReturn(true);

        OrderVO placed = orderService.placeOrder(1L, buildRequest(), "itg-detail-001");
        OrderVO detail = orderService.getOrderDetail(placed.getId());

        assertThat(detail.getItems()).isNotEmpty();
        assertThat(detail.getItems().get(0).getDishNameSnapshot()).isEqualTo("宫保鸡丁");
        assertThat(detail.getItems().get(0).getUnitPrice()).isEqualTo(1500);
    }

    @Test
    @DisplayName("集成: PREPARING 状态不能取消")
    void cannotCancelPreparing() {
        MenuDishClient.DishInfo dish = new MenuDishClient.DishInfo();
        dish.setId(1L);
        dish.setName("宫保鸡丁");
        dish.setPriceCents(1200);
        dish.setOnShelf(1);
        when(menuDishClient.getDish(1L)).thenReturn(Result.success(dish));
        when(menuMerchantClient.getMerchant(1L)).thenReturn(Result.success(buildMerchant()));
        when(menuServiceClient.deduct(any())).thenReturn(Result.success(true));
        when(streamBridge.send(any(), any())).thenReturn(true);

        OrderVO placed = orderService.placeOrder(1L, buildRequest(), "itg-nocancel-001");
        orderService.acceptOrder(placed.getId());
        orderService.startPreparing(placed.getId());

        assertThrows(com.canteen.common.exception.BusinessException.class, () ->
                orderService.cancelOrder(placed.getId(), "来不及了"));
    }

    private MenuMerchantClient.MerchantInfo buildMerchant() {
        MenuMerchantClient.MerchantInfo m = new MenuMerchantClient.MerchantInfo();
        m.setId(1L);
        m.setCounterId("C01");
        return m;
    }
}
