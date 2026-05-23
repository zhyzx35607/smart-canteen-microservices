package com.canteen.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PickupEnqueueRequest {

    private Long orderId;
    private String pickupCode;
    private Long userId;
    private String counterId;
}
