package com.canteen.pickup.feign;

import com.canteen.common.config.InternalTokenInterceptor;
import com.canteen.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "order-service", path = "/internal/orders", configuration = InternalTokenInterceptor.class)
public interface OrderServiceClient {

    @PostMapping("/{id}/picked")
    Result<Void> markPickedUp(@PathVariable("id") Long orderId);
}
