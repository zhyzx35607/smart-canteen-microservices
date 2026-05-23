package com.canteen.pickup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueueEntry {

    private Long orderId;
    private String pickupCode;
    private Long userId;
}
