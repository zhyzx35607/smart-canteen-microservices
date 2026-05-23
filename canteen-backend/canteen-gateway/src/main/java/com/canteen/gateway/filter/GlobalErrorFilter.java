package com.canteen.gateway.filter;

import com.canteen.common.result.Result;
import com.canteen.common.result.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(-1)
@RequiredArgsConstructor
public class GlobalErrorFilter implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        log.error("Gateway error: path={}, error={}", exchange.getRequest().getURI().getPath(), ex.getMessage(), ex);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ResultCode resultCode = ResultCode.UNKNOWN_ERROR;

        String message = ex.getMessage();
        if (message != null) {
            if (message.contains("404")) {
                status = HttpStatus.NOT_FOUND;
                resultCode = ResultCode.GATEWAY_SERVICE_UNAVAILABLE;
            } else if (message.contains("503") || message.contains("Service Unavailable")) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                resultCode = ResultCode.GATEWAY_SERVICE_UNAVAILABLE;
            } else if (message.contains("429") || message.contains("Too Many Requests")) {
                status = HttpStatus.TOO_MANY_REQUESTS;
                resultCode = ResultCode.GATEWAY_RATE_LIMITED;
            }
        }

        Result<Void> result = Result.fail(resultCode);
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        result.setTraceId(traceId);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(result);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return exchange.getResponse().setComplete();
        }
    }
}
