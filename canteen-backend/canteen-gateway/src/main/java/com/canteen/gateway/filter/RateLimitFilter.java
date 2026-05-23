package com.canteen.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * 业务粒度的精细限流（IP/用户级），基于 Redis 计数器实现。
 *
 * 与 SentinelGatewayConfig 的职责划分：
 * - Sentinel：按路由 ID（user/menu/order/pickup）做粗粒度 QPS 流控，作为全局保护伞。
 * - 本过滤器：按"接口语义 + IP/用户维度"做差异化限流，例如：
 *     • 登录/注册接口 IP 级严格限流（防暴力破解）
 *     • 下单接口用户级限流（防刷单）
 *     • 通用 IP 级 / 已登录用户 级 兜底限流
 *   这些规则与具体业务耦合，比 Sentinel 单纯按路由限流更精准。
 *
 * 执行顺序：order = -60，先于 JwtAuthGlobalFilter (-50)，限流不消耗鉴权成本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final StringRedisTemplate stringRedisTemplate;

    // IP 级限流：每个 IP 每分钟最多 100 次
    private static final String IP_LIMIT_PREFIX = "ratelimit:ip:";
    private static final int IP_LIMIT_MAX = 100;
    private static final int IP_LIMIT_WINDOW_SEC = 60;

    // 用户级限流：每个用户每分钟最多 60 次
    private static final String USER_LIMIT_PREFIX = "ratelimit:user:";
    private static final int USER_LIMIT_MAX = 60;
    private static final int USER_LIMIT_WINDOW_SEC = 60;

    // 登录/注册接口 IP 级限流：每分钟最多 10 次
    private static final String AUTH_LIMIT_PREFIX = "ratelimit:auth:ip:";
    private static final int AUTH_LIMIT_MAX = 10;
    private static final int AUTH_LIMIT_WINDOW_SEC = 60;

    // 下单接口用户级限流：每分钟最多 20 次
    private static final String ORDER_LIMIT_PREFIX = "ratelimit:order:user:";
    private static final int ORDER_LIMIT_MAX = 20;
    private static final int ORDER_LIMIT_WINDOW_SEC = 60;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. 登录/注册接口：IP 级严格限流
        if (path.contains("/auth/login") || path.contains("/auth/register")) {
            String ip = getClientIp(exchange);
            if (isRateLimited(AUTH_LIMIT_PREFIX + ip, AUTH_LIMIT_MAX, AUTH_LIMIT_WINDOW_SEC)) {
                log.warn("Auth rate limited: ip={}, path={}", ip, path);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
        }

        // 2. 下单接口：用户级限流
        if (path.contains("/orders") && "POST".equals(exchange.getRequest().getMethod().name())) {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && isRateLimited(ORDER_LIMIT_PREFIX + userId, ORDER_LIMIT_MAX, ORDER_LIMIT_WINDOW_SEC)) {
                log.warn("Order rate limited: userId={}", userId);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
        }

        // 3. 通用 IP 级限流
        String ip = getClientIp(exchange);
        if (isRateLimited(IP_LIMIT_PREFIX + ip, IP_LIMIT_MAX, IP_LIMIT_WINDOW_SEC)) {
            log.warn("IP rate limited: ip={}", ip);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        // 4. 已登录用户限流
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId != null && isRateLimited(USER_LIMIT_PREFIX + userId, USER_LIMIT_MAX, USER_LIMIT_WINDOW_SEC)) {
            log.warn("User rate limited: userId={}", userId);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    /**
     * 滑动窗口计数器限流
     * 使用 Redis INCR + EXPIRE 实现固定窗口
     */
    private boolean isRateLimited(String key, int maxCount, int windowSec) {
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, windowSec, TimeUnit.SECONDS);
            }
            return count != null && count > maxCount;
        } catch (Exception e) {
            log.error("Rate limit check failed for key={}", key, e);
            // 限流检查失败时放行，避免 Redis 故障影响正常请求
            return false;
        }
    }

    private String getClientIp(ServerWebExchange exchange) {
        String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
        }
        // X-Forwarded-For 可能含多个 IP，取第一个
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    @Override
    public int getOrder() {
        // 在 JWT 校验之前执行（JWT 是 -50），确保限流优先
        return -60;
    }
}
