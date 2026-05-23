package com.canteen.pickup.dto;

import lombok.Data;

import java.util.List;

@Data
public class QueueScreenVO {

    private String counterId;
    private QueueEntry currentCalling;
    private List<QueueEntry> waitingList;
    private List<QueueEntry> historyList;
}
