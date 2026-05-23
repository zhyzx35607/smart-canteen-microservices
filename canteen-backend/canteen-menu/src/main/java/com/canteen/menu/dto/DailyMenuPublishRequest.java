package com.canteen.menu.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class DailyMenuPublishRequest {

    @NotNull(message = "商户ID不能为空")
    private Long merchantId;

    @NotNull(message = "营业日期不能为空")
    private LocalDate bizDate;

    @NotNull(message = "开售时间不能为空")
    private LocalTime sellStart;

    @NotNull(message = "停售时间不能为空")
    private LocalTime sellEnd;

    @NotNull(message = "菜品列表不能为空")
    private List<MenuItem> items;

    @Data
    public static class MenuItem {
        @NotNull
        private Long dishId;
        @NotNull
        private Integer stockInit;
    }
}
