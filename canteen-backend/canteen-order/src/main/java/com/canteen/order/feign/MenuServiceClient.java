package com.canteen.order.feign;

import com.canteen.common.config.InternalTokenInterceptor;
import com.canteen.common.result.Result;
import com.canteen.order.dto.StockDeductRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "menu-service", contextId = "menuStockClient",
        path = "/internal/stock", configuration = InternalTokenInterceptor.class)
public interface MenuServiceClient {

    @PostMapping("/deduct")
    Result<Boolean> deduct(@RequestBody StockDeductRequest req);

    @PostMapping("/restore")
    Result<Void> restore(@RequestBody StockDeductRequest req);
}
