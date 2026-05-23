package com.canteen.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PlaceOrderRequest {

    @NotNull(message = "商户ID不能为空")
    private Long merchantId;

    @NotEmpty(message = "订单明细不能为空")
    private List<OrderItemDTO> items;

    @Data
    public static class OrderItemDTO {
        @NotNull(message = "菜品ID不能为空")
        private Long dishId;
        @NotNull(message = "数量不能为空")
        private Integer quantity;
    }
}
