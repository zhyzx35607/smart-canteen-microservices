package com.canteen.common.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalTokenInterceptor implements RequestInterceptor {

    private static final String HEADER = "X-Internal-Token";

    @Value("${internal.token}")
    private String internalToken;

    @Override
    public void apply(RequestTemplate template) {
        template.header(HEADER, internalToken);
    }
}
