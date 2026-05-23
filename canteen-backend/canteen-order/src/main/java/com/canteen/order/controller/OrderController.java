package com.canteen.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.canteen.common.result.Result;
import com.canteen.order.dto.OrderVO;
import com.canteen.order.dto.PlaceOrderRequest;
import com.canteen.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Result<OrderVO> placeOrder(@RequestHeader("X-User-Id") Long userId,
                                      @Valid @RequestBody PlaceOrderRequest req,
                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(orderService.placeOrder(userId, req, idempotencyKey));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> getOrder(@PathVariable Long id) {
        return Result.success(orderService.getOrderDetail(id));
    }

    @GetMapping
    public Result<Page<OrderVO>> listOrders(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return Result.success(orderService.listOrders(userId, page, size));
    }

    @PutMapping("/{id}/accept")
    public Result<OrderVO> acceptOrder(@PathVariable Long id) {
        return Result.success(orderService.acceptOrder(id));
    }

    @PutMapping("/{id}/start")
    public Result<OrderVO> startPreparing(@PathVariable Long id) {
        return Result.success(orderService.startPreparing(id));
    }

    @PutMapping("/{id}/ready")
    public Result<OrderVO> readyForPickup(@PathVariable Long id) {
        return Result.success(orderService.readyForPickup(id));
    }

    @PutMapping("/{id}/cancel")
    public Result<OrderVO> cancelOrder(@PathVariable Long id,
                                       @RequestParam(defaultValue = "用户主动取消") String reason) {
        return Result.success(orderService.cancelOrder(id, reason));
    }
}
