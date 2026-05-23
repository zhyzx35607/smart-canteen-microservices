package com.canteen.menu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class StockDeductRequest {

    private List<StockItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockItem {
        private Long dishId;
        private Integer quantity;
    }
}
