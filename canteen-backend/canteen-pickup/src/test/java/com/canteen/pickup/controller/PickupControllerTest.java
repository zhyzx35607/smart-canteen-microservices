package com.canteen.pickup.controller;

import com.canteen.pickup.dto.QueueEntry;
import com.canteen.pickup.dto.QueueScreenVO;
import com.canteen.pickup.service.PickupQueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PickupController.class)
class PickupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PickupQueueService pickupQueueService;

    // Prevent RedissonConfig, RedisConfig from failing context load
    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    @DisplayName("GET /queues/{counterId} - 获取大屏数据")
    void testGetScreenData() throws Exception {
        QueueScreenVO vo = new QueueScreenVO();
        vo.setCounterId("C01");
        when(pickupQueueService.getScreenData("C01")).thenReturn(vo);

        mockMvc.perform(get("/queues/C01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.counterId").value("C01"));
    }

    @Test
    @DisplayName("POST /queues/{counterId}/call - 叫号")
    void testCall() throws Exception {
        QueueEntry entry = new QueueEntry(1L, "C01000001", 100L);
        when(pickupQueueService.call("C01")).thenReturn(entry);

        mockMvc.perform(post("/queues/C01/call"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(1))
                .andExpect(jsonPath("$.data.pickupCode").value("C01000001"));
    }
}
