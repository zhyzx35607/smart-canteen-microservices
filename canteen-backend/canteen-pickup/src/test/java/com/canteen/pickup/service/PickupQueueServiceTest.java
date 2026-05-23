package com.canteen.pickup.service;

import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.ResultCode;
import com.canteen.pickup.dto.*;
import com.canteen.pickup.feign.OrderServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PickupQueueServiceTest {

    @InjectMocks
    private PickupQueueService pickupQueueService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private OrderServiceClient orderServiceClient;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Inject objectMapper manually since it's not a Spring bean in test
        try {
            var field = PickupQueueService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(pickupQueueService, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("入队成功")
    void testEnqueue() {
        var listOps = mock(ListOperations.class);
        var valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForList()).thenReturn(listOps);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        PickupEnqueueRequest req = new PickupEnqueueRequest(1L, "C01000001", 100L, "C01");
        pickupQueueService.enqueue(req);

        verify(listOps).rightPush(eq("queue:C01"), anyString());
        verify(valueOps).set(eq("code:C01:C01000001"), eq("1"), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("叫号成功: 队列有元素")
    void testCallSuccess() {
        var listOps = mock(ListOperations.class);
        var valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForList()).thenReturn(listOps);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        QueueEntry entry = new QueueEntry(1L, "C01000001", 100L);
        try {
            String json = objectMapper.writeValueAsString(entry);
            when(listOps.leftPop("queue:C01")).thenReturn(json);
        } catch (Exception e) {
            fail("JSON serialization failed");
        }

        QueueEntry result = pickupQueueService.call("C01");

        assertNotNull(result);
        assertEquals(1L, result.getOrderId());
        assertEquals("C01000001", result.getPickupCode());
        verify(messagingTemplate).convertAndSend(eq("/topic/screen/C01"), any(CallEvent.class));
    }

    @Test
    @DisplayName("叫号失败: 队列为空")
    void testCallEmptyQueue() {
        var listOps = mock(ListOperations.class);
        when(stringRedisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.leftPop("queue:C01")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pickupQueueService.call("C01"));
        assertEquals(ResultCode.PICKUP_QUEUE_EMPTY.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("核销成功")
    void testVerify() {
        var valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("code:C01:C01000001")).thenReturn("1");
        when(valueOps.get("queue:current:C01")).thenReturn(null);

        VerifyRequest req = new VerifyRequest();
        req.setCounterId("C01");
        req.setPickupCode("C01000001");

        pickupQueueService.verify(req);

        verify(orderServiceClient).markPickedUp(1L);
        verify(stringRedisTemplate).delete("code:C01:C01000001");
        verify(stringRedisTemplate).delete("queue:current:C01");
    }

    @Test
    @DisplayName("核销失败: 取餐码不存在")
    void testVerifyCodeNotFound() {
        var valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        VerifyRequest req = new VerifyRequest();
        req.setPickupCode("INVALID");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pickupQueueService.verify(req));
        assertEquals(ResultCode.PICKUP_CODE_NOT_FOUND.getCode(), ex.getCode());
    }
}
