package com.canteen.menu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("daily_menu")
public class DailyMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private LocalDate bizDate;

    private LocalTime sellStart;

    private LocalTime sellEnd;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(delval = "NOW()", value = "NULL")
    private LocalDateTime deletedAt;
}
