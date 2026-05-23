package com.canteen.user.controller;

import com.canteen.common.result.Result;
import com.canteen.user.dto.*;
import com.canteen.user.service.AuthService;
import com.canteen.user.service.UserProfileService;
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

@WebMvcTest({AuthController.class, UserController.class})
class UserControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserProfileService userProfileService;

    @Test
    @DisplayName("POST /auth/register - 注册")
    void testRegister() throws Exception {
        TokenResponse tokenResp = TokenResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .expiresIn(900L)
                .userId(1L)
                .build();
        when(authService.register(any(RegisterRequest.class))).thenReturn(tokenResp);

        RegisterRequest req = new RegisterRequest();
        req.setPhone("13800138000");
        req.setPassword("password123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("POST /auth/login - 登录")
    void testLogin() throws Exception {
        TokenResponse tokenResp = TokenResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .expiresIn(900L)
                .userId(1L)
                .build();
        when(authService.login(any(LoginRequest.class))).thenReturn(tokenResp);

        LoginRequest req = new LoginRequest();
        req.setPhone("13800138000");
        req.setLoginType("password");
        req.setPassword("password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("GET /users/me - 获取当前用户")
    void testGetMe() throws Exception {
        UserVO vo = new UserVO();
        vo.setId(1L);
        vo.setPhone("13800138000");
        vo.setNickname("测试用户");
        when(userProfileService.getMe(1L)).thenReturn(vo);

        mockMvc.perform(get("/users/me")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("13800138000"));
    }
}
