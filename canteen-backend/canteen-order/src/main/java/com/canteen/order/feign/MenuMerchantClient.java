package com.canteen.order.feign;

import com.canteen.common.config.InternalTokenInterceptor;
import com.canteen.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "menu-service", contextId = "menuMerchantClient",
        path = "/internal/merchants", configuration = InternalTokenInterceptor.class)
public interface MenuMerchantClient {

    @GetMapping("/{id}")
    Result<MerchantInfo> getMerchant(@PathVariable Long id);

    @lombok.Data
    class MerchantInfo {
        private Long id;
        private String name;
        private String counterId;
    }
}
