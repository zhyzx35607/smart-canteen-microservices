package com.canteen.menu.controller;

import com.canteen.common.result.Result;
import com.canteen.menu.dto.DailyMenuPublishRequest;
import com.canteen.menu.dto.DailyMenuVO;
import com.canteen.menu.service.DailyMenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/daily")
@RequiredArgsConstructor
public class DailyMenuController {

    private final DailyMenuService dailyMenuService;

    @PostMapping
    public Result<DailyMenuVO> publishMenu(@Valid @RequestBody DailyMenuPublishRequest req) {
        return Result.success(dailyMenuService.publishMenu(req));
    }

    @GetMapping
    public Result<DailyMenuVO> getTodayMenu(@RequestParam(required = false) Long merchantId) {
        return Result.success(dailyMenuService.getTodayMenu(merchantId));
    }

    @GetMapping("/{id}")
    public Result<DailyMenuVO> getMenuDetail(@PathVariable Long id) {
        return Result.success(dailyMenuService.getMenuDetail(id));
    }
}
