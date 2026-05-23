package com.canteen.menu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("daily_menu_item")
public class DailyMenuItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dailyMenuId;

    private Long dishId;

    private Integer stockInit;

    private Integer stockLeft;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
