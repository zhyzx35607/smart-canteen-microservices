package com.canteen.menu.controller;

import com.canteen.common.result.Result;
import com.canteen.menu.dto.StockDeductRequest;
import com.canteen.menu.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/stock")
@RequiredArgsConstructor
public class InternalStockController {

    private final StockService stockService;

    @Value("${internal.token}")
    private String internalToken;

    @PostMapping("/deduct")
    public Result<Boolean> deduct(@RequestHeader("X-Internal-Token") String token,
                                  @RequestBody StockDeductRequest req) {
        validateInternalToken(token);
        boolean success = stockService.deduct(req.getItems());
        return Result.success(success);
    }

    @PostMapping("/restore")
    public Result<Void> restore(@RequestHeader("X-Internal-Token") String token,
                                @RequestBody StockDeductRequest req) {
        validateInternalToken(token);
        stockService.restore(req.getItems());
        return Result.success();
    }

    private void validateInternalToken(String token) {
        if (!internalToken.equals(token)) {
            throw new com.canteen.common.exception.BusinessException(
                    com.canteen.common.result.ResultCode.GATEWAY_AUTH_FAILED, "内部接口Token无效");
        }
    }
}
