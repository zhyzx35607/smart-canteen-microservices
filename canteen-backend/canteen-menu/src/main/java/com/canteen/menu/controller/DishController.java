package com.canteen.menu.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.canteen.common.result.Result;
import com.canteen.menu.dto.DishCreateRequest;
import com.canteen.menu.dto.DishVO;
import com.canteen.menu.service.DishService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dishes")
@RequiredArgsConstructor
public class DishController {

    private final DishService dishService;

    @GetMapping
    public Result<Page<DishVO>> listDishes(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Integer onShelf,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(dishService.listDishes(merchantId, onShelf, page, size));
    }

    @GetMapping("/{id}")
    public Result<DishVO> getDish(@PathVariable Long id) {
        return Result.success(dishService.getDish(id));
    }

    @PostMapping
    public Result<DishVO> createDish(@Valid @RequestBody DishCreateRequest req) {
        return Result.success(dishService.createDish(req));
    }

    @PutMapping("/{id}")
    public Result<DishVO> updateDish(@PathVariable Long id, @Valid @RequestBody DishCreateRequest req) {
        return Result.success(dishService.updateDish(id, req));
    }

    @PutMapping("/{id}/on-shelf")
    public Result<Void> toggleShelf(@PathVariable Long id) {
        dishService.toggleShelf(id);
        return Result.success();
    }
}
