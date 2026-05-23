package com.canteen.order.controller;

import com.canteen.common.result.Result;
import com.canteen.order.dto.OrderVO;
import com.canteen.order.dto.PlaceOrderRequest;
import com.canteen.order.mapper.OrderItemMapper;
import com.canteen.order.mapper.OrderMapper;
import com.canteen.order.service.OrderService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    // Prevent MyBatis @MapperScan, RedissonConfig, RedisConfig from failing context load
    @MockBean
    private OrderMapper orderMapper;

    @MockBean
    private OrderItemMapper orderItemMapper;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    @DisplayName("POST /orders - 下单成功")
    void testPlaceOrder() throws Exception {
        OrderVO vo = new OrderVO();
        vo.setId(1L);
        vo.setStatus("PLACED");
        vo.setTotalCents(2400);
        when(orderService.placeOrder(anyLong(), any(PlaceOrderRequest.class), any())).thenReturn(vo);

        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setMerchantId(1L);
        PlaceOrderRequest.OrderItemDTO item = new PlaceOrderRequest.OrderItemDTO();
        item.setDishId(1L);
        item.setQuantity(2);
        req.setItems(List.of(item));

        mockMvc.perform(post("/orders")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("PLACED"));
    }

    @Test
    @DisplayName("GET /orders/{id} - 获取订单详情")
    void testGetOrder() throws Exception {
        OrderVO vo = new OrderVO();
        vo.setId(1L);
        vo.setStatus("PLACED");
        when(orderService.getOrderDetail(1L)).thenReturn(vo);

        mockMvc.perform(get("/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("PUT /orders/{id}/accept - 商家接单")
    void testAcceptOrder() throws Exception {
        OrderVO vo = new OrderVO();
        vo.setId(1L);
        vo.setStatus("ACCEPTED");
        when(orderService.acceptOrder(1L)).thenReturn(vo);

        mockMvc.perform(put("/orders/1/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("PUT /orders/{id}/cancel - 取消订单")
    void testCancelOrder() throws Exception {
        OrderVO vo = new OrderVO();
        vo.setId(1L);
        vo.setStatus("CANCELED");
        when(orderService.cancelOrder(eq(1L), anyString())).thenReturn(vo);

        mockMvc.perform(put("/orders/1/cancel")
                        .param("reason", "不想要了"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    @DisplayName("GET /orders - 订单列表")
    void testListOrders() throws Exception {
        Page<OrderVO> page = new Page<>(1, 20, 1);
        page.setRecords(List.of());
        when(orderService.listOrders(any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/orders")
                        .header("X-User-Id", "1")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
