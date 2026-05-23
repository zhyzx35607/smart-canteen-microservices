package com.canteen.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.canteen.common.result.Result;
import com.canteen.common.result.ResultCode;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.HashSet;
import java.util.Set;

/**
 * Sentinel 网关粗粒度路由限流。
 * 与 RateLimitFilter 协同：本类按路由 ID 设全局 QPS 上限，作为兜底保护；
 * RateLimitFilter 在路由内部按 IP/用户做精细限流，二者叠加生效。
 */
@Configuration
public class SentinelGatewayConfig {

    @PostConstruct
    public void init() {
        // 限流规则
        Set<GatewayFlowRule> rules = new HashSet<>();
        rules.add(new GatewayFlowRule("user").setCount(100).setIntervalSec(1));
        rules.add(new GatewayFlowRule("menu").setCount(200).setIntervalSec(1));
        rules.add(new GatewayFlowRule("order").setCount(50).setIntervalSec(1));
        rules.add(new GatewayFlowRule("pickup").setCount(100).setIntervalSec(1));
        GatewayRuleManager.loadRules(rules);

        // 自定义限流响应
        GatewayCallbackManager.setBlockHandler((exchange, t) ->
            ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.fail(ResultCode.GATEWAY_RATE_LIMITED)));
    }
}
