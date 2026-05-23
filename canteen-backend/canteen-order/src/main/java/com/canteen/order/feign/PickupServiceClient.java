package com.canteen.order.feign;

import com.canteen.common.config.InternalTokenInterceptor;
import com.canteen.common.result.Result;
import com.canteen.order.dto.PickupEnqueueRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "pickup-service", path = "/internal/queues", configuration = InternalTokenInterceptor.class)
public interface PickupServiceClient {

    @PostMapping("/enqueue")
    Result<Void> enqueue(@RequestBody PickupEnqueueRequest req);
}
