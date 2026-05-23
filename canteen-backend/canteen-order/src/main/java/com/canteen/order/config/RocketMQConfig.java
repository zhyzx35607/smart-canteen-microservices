package com.canteen.order.config;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RocketMQ 配置：封装延时消息发送
 * Spring Cloud Stream RocketMQ 延时级别映射：
 * 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
 *  1   2   3   4   5  6  7  8  9 10 11 12 13  14  15  16  17  18
 * level 16 = 30min
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RocketMQConfig {

    private final StreamBridge streamBridge;

    private static final String DELAY_LEVEL_30MIN = "16";

    /**
     * 发送订单超时延时消息（30分钟后消费）
     */
    public void sendOrderTimeoutMessage(Long orderId) {
        Message<String> message = MessageBuilder
                .withPayload(String.valueOf(orderId))
                .setHeader("DELAY", DELAY_LEVEL_30MIN)
                .build();

        boolean sent = streamBridge.send("orderTimeout-out-0", message);
        if (sent) {
            log.info("Order timeout message sent: orderId={}", orderId);
        } else {
            log.error("Failed to send order timeout message: orderId={}", orderId);
        }
    }
}
