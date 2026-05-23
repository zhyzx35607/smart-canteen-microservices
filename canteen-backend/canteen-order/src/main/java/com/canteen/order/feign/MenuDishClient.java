package com.canteen.order.feign;

import com.canteen.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "menu-service", contextId = "menuDishClient", path = "/dishes")
public interface MenuDishClient {

    @GetMapping("/{id}")
    Result<DishInfo> getDish(@PathVariable Long id);

    @lombok.Data
    class DishInfo {
        private Long id;
        private String name;
        private Integer priceCents;
        private Integer onShelf;
    }
}
