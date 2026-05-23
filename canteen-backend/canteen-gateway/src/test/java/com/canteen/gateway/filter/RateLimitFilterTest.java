package com.canteen.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private GatewayFilterChain chain;
    private RateLimitFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter = new RateLimitFilter(redis);
    }

    @Test
    @DisplayName("计数未超限：放行")
    void testUnderLimitPassThrough() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/menu/dishes")
                        .header("X-User-Id", "1").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("登录接口超过 IP 限额：返回 429")
    void testAuthIpRateLimited() {
        when(valueOps.increment(anyString())).thenReturn(11L);

        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/user/auth/login").build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }
}
