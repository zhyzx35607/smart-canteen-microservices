package com.canteen.pickup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CallEvent {

    private String counterId;
    private QueueEntry currentCalling;
    private String type; // "CALL" or "VERIFY"
}
