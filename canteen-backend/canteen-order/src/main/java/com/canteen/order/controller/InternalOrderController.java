package com.canteen.order.controller;

import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.Result;
import com.canteen.common.result.ResultCode;
import com.canteen.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    @Value("${internal.token}")
    private String internalToken;

    @PostMapping("/{id}/picked")
    public Result<Void> markPickedUp(@RequestHeader("X-Internal-Token") String token,
                                     @PathVariable Long id) {
        validateInternalToken(token);
        orderService.markPickedUp(id);
        return Result.success();
    }

    private void validateInternalToken(String token) {
        if (!internalToken.equals(token)) {
            throw new BusinessException(ResultCode.GATEWAY_AUTH_FAILED, "内部接口Token无效");
        }
    }
}
