package com.canteen.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class DishCreateRequest {

    @NotNull(message = "商户ID不能为空")
    private Long merchantId;

    @NotBlank(message = "菜品名不能为空")
    private String name;

    @NotNull(message = "价格不能为空")
    @Positive(message = "价格必须为正数")
    private Integer priceCents;

    private String imageUrl;

    private Integer threshold = 5;
}
