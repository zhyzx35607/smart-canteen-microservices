package com.canteen.pickup.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyRequest {

    @NotBlank(message = "取餐码不能为空")
    private String pickupCode;

    private String counterId;
}
