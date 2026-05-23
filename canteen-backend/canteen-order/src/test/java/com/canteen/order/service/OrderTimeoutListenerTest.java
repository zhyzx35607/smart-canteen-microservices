package com.canteen.order.service;

import com.canteen.order.listener.OrderTimeoutListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderTimeoutListenerTest {

    @InjectMocks
    private OrderTimeoutListener orderTimeoutListener;

    @Mock
    private OrderService orderService;

    @Test
    @DisplayName("RocketMQ: 收到超时消息 → 调用 timeoutCancel")
    void testOrderTimeout() {
        Consumer<String> consumer = orderTimeoutListener.orderTimeout();
        consumer.accept("123");
        verify(orderService).timeoutCancel(123L);
    }

    @Test
    @DisplayName("RocketMQ: 无效 orderId → 不抛异常,不调用 timeoutCancel")
    void testInvalidOrderId() {
        Consumer<String> consumer = orderTimeoutListener.orderTimeout();
        consumer.accept("invalid");
        verify(orderService, never()).timeoutCancel(anyLong());
    }

    @Test
    @DisplayName("RocketMQ: timeoutCancel 抛异常 → 被 catch 不向上传播")
    void testTimeoutCancelThrowsException() {
        doThrow(new RuntimeException("DB error")).when(orderService).timeoutCancel(1L);
        Consumer<String> consumer = orderTimeoutListener.orderTimeout();
        // 不应抛异常
        consumer.accept("1");
        verify(orderService).timeoutCancel(1L);
    }
}
