package com.canteen.menu.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DishVO {

    private Long id;
    private Long merchantId;
    private String name;
    private Integer priceCents;
    private String imageUrl;
    private Integer onShelf;
    private Integer threshold;
    private LocalDateTime createdAt;
}
