package com.canteen.pickup;

import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.Result;
import com.canteen.pickup.dto.PickupEnqueueRequest;
import com.canteen.pickup.dto.QueueEntry;
import com.canteen.pickup.dto.QueueScreenVO;
import com.canteen.pickup.dto.VerifyRequest;
import com.canteen.pickup.feign.OrderServiceClient;
import com.canteen.pickup.service.PickupQueueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
class PickupQueueServiceIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.autoconfigure.exclude",
                () -> "com.alibaba.cloud.nacos.NacosConfigAutoConfiguration,"
                        + "com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientAutoConfiguration,"
                        + "com.alibaba.cloud.sentinel.SentinelWebAutoConfiguration,"
                        + "org.springframework.cloud.stream.function.StreamFunctionAutoConfiguration");
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
        registry.add("spring.cloud.sentinel.enabled", () -> "false");
        registry.add("spring.cloud.stream.rocketmq.binder.name-server", () -> "localhost:19876");
        registry.add("internal.token", () -> "test-internal-token");
    }

    @Autowired
    private PickupQueueService pickupQueueService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private OrderServiceClient orderServiceClient;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @AfterEach
    void tearDown() {
        Set<String> keys = stringRedisTemplate.keys("queue:*");
        if (keys != null) stringRedisTemplate.delete(keys);
        keys = stringRedisTemplate.keys("code:*");
        if (keys != null) stringRedisTemplate.delete(keys);
        keys = stringRedisTemplate.keys("pickup:counter:*");
        if (keys != null) stringRedisTemplate.delete(keys);
    }

    @Test
    @DisplayName("集成: 入队 → 叫号 → 核销 完整流程")
    void fullQueueFlow() {
        doNothing().when(orderServiceClient).markPickedUp(anyLong());

        PickupEnqueueRequest enq = new PickupEnqueueRequest(100L, "C01000001", 1L, "C01");
        pickupQueueService.enqueue(enq);

        QueueScreenVO screen = pickupQueueService.getScreenData("C01");
        assertThat(screen.getWaitingList()).hasSize(1);
        assertThat(screen.getWaitingList().get(0).getPickupCode()).isEqualTo("C01000001");

        QueueEntry called = pickupQueueService.call("C01");
        assertThat(called.getOrderId()).isEqualTo(100L);
        assertThat(called.getPickupCode()).isEqualTo("C01000001");

        screen = pickupQueueService.getScreenData("C01");
        assertThat(screen.getCurrentCalling()).isNotNull();
        assertThat(screen.getWaitingList()).isEmpty();
        assertThat(screen.getHistoryList()).hasSize(1);

        VerifyRequest verify = new VerifyRequest();
        verify.setPickupCode("C01000001");
        verify.setCounterId("C01");
        pickupQueueService.verify(verify);

        screen = pickupQueueService.getScreenData("C01");
        assertThat(screen.getCurrentCalling()).isNull();
    }

    @Test
    @DisplayName("集成: 队列为空时叫号 → 抛 PICKUP_QUEUE_EMPTY")
    void callEmptyQueue() {
        assertThrows(BusinessException.class, () -> pickupQueueService.call("C02"));
    }

    @Test
    @DisplayName("集成: 取餐码不存在核销 → 抛 PICKUP_CODE_NOT_FOUND")
    void verifyInvalidCode() {
        VerifyRequest req = new VerifyRequest();
        req.setPickupCode("INVALID");
        req.setCounterId("C01");

        assertThrows(BusinessException.class, () -> pickupQueueService.verify(req));
    }

    @Test
    @DisplayName("集成: 多窗口独立队列验证")
    void multipleCounterQueues() {
        PickupEnqueueRequest enq1 = new PickupEnqueueRequest(1L, "C0100001", 1L, "C01");
        PickupEnqueueRequest enq2 = new PickupEnqueueRequest(2L, "C0200001", 2L, "C02");

        pickupQueueService.enqueue(enq1);
        pickupQueueService.enqueue(enq2);

        assertThat(pickupQueueService.getScreenData("C01").getWaitingList()).hasSize(1);
        assertThat(pickupQueueService.getScreenData("C02").getWaitingList()).hasSize(1);

        QueueEntry called = pickupQueueService.call("C01");
        assertThat(called.getPickupCode()).isEqualTo("C0100001");
        assertThat(pickupQueueService.getScreenData("C02").getWaitingList()).hasSize(1);
    }
}
