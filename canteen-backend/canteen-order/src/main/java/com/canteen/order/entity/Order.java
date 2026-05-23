package com.canteen.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(delval = "NOW()", value = "NULL")
    private LocalDateTime deletedAt;

    // ========== 状态常量 ==========
    public static final String STATUS_PLACED = "PLACED";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_PREPARING = "PREPARING";
    public static final String STATUS_WAITING_PICKUP = "WAITING_PICKUP";
    public static final String STATUS_PICKED_UP = "PICKED_UP";
    public static final String STATUS_CANCELED = "CANCELED";
}
