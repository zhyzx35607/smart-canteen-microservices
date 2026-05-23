package com.canteen.menu.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class DailyMenuVO {

    private Long id;
    private Long merchantId;
    private String merchantName;
    private LocalDate bizDate;
    private LocalTime sellStart;
    private LocalTime sellEnd;
    private List<MenuItemVO> items;

    @Data
    public static class MenuItemVO {
        private Long dishId;
        private String dishName;
        private Integer priceCents;
        private Integer stockInit;
        private Integer stockLeft;
    }
}
