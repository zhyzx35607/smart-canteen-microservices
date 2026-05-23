package com.canteen.order.listener;

import com.canteen.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * RocketMQ 延时消息消费者：消费 order.timeout 主题
 * 订单下单 30min 后若仍未被接单/支付，自动取消并回滚库存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutListener {

    private final OrderService orderService;

    @Bean
    public Consumer<String> orderTimeout() {
        return orderIdStr -> {
            try {
                Long orderId = Long.valueOf(orderIdStr);
                log.info("Received order timeout message: orderId={}", orderId);
                orderService.timeoutCancel(orderId);
            } catch (NumberFormatException e) {
                log.error("Invalid orderId in timeout message: {}", orderIdStr, e);
            } catch (Exception e) {
                log.error("Failed to process order timeout: orderId={}", orderIdStr, e);
            }
        };
    }
}
