package com.canteen.menu.controller;

import com.canteen.common.result.Result;
import com.canteen.menu.dto.DishCreateRequest;
import com.canteen.menu.dto.DishVO;
import com.canteen.menu.service.DishService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DishController.class)
class DishControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DishService dishService;

    @Test
    @DisplayName("POST /dishes - 新增菜品")
    void testCreateDish() throws Exception {
        DishVO vo = new DishVO();
        vo.setId(1L);
        vo.setName("宫保鸡丁");
        vo.setPriceCents(1200);
        when(dishService.createDish(any(DishCreateRequest.class))).thenReturn(vo);

        DishCreateRequest req = new DishCreateRequest();
        req.setMerchantId(1L);
        req.setName("宫保鸡丁");
        req.setPriceCents(1200);

        mockMvc.perform(post("/dishes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("宫保鸡丁"));
    }

    @Test
    @DisplayName("PUT /dishes/{id}/on-shelf - 上下架切换")
    void testToggleShelf() throws Exception {
        mockMvc.perform(put("/dishes/1/on-shelf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
