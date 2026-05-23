package com.canteen.order.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVO {

    private Long id;
    private Long userId;
    private Long merchantId;
    private String counterId;
    private String status;
    private Integer totalCents;
    private String pickupCode;
    private LocalDateTime placedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime readyAt;
    private LocalDateTime pickedAt;
    private LocalDateTime canceledAt;
    private String cancelReason;
    private List<OrderItemVO> items;

    @Data
    public static class OrderItemVO {
        private Long dishId;
        private String dishNameSnapshot;
        private Integer unitPrice;
        private Integer quantity;
    }
}
