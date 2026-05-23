package com.canteen.gateway.filter;

import com.canteen.common.util.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JwtAuthGlobalFilterTest {

    private JwtTokenProvider jwtTokenProvider;
    private StringRedisTemplate redis;
    private GatewayFilterChain chain;
    private JwtAuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        redis = mock(StringRedisTemplate.class);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter = new JwtAuthGlobalFilter(jwtTokenProvider, redis);
        ReflectionTestUtils.setField(filter, "whitelist",
                List.of("/api/user/auth/**", "/ws/screen/**", "/actuator/**"));
    }

    @Test
    @DisplayName("白名单路径直接放行，不校验 Token")
    void testWhitelistPassThrough() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/auth/login").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("缺少 Authorization header 返回 401")
    void testMissingAuthHeader() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/menu/dishes").build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("无效 Token 返回 401")
    void testInvalidToken() {
        when(jwtTokenProvider.validate("bad")).thenReturn(false);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/menu/dishes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad").build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
}
