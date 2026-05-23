package com.canteen.pickup.controller;

import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.Result;
import com.canteen.common.result.ResultCode;
import com.canteen.pickup.dto.PickupEnqueueRequest;
import com.canteen.pickup.service.PickupQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/queues")
@RequiredArgsConstructor
public class InternalQueueController {

    private final PickupQueueService pickupQueueService;

    @Value("${internal.token}")
    private String internalToken;

    @PostMapping("/enqueue")
    public Result<Void> enqueue(@RequestHeader("X-Internal-Token") String token,
                                @RequestBody PickupEnqueueRequest req) {
        validateInternalToken(token);
        pickupQueueService.enqueue(req);
        return Result.success();
    }

    private void validateInternalToken(String token) {
        if (!internalToken.equals(token)) {
            throw new BusinessException(ResultCode.GATEWAY_AUTH_FAILED, "内部接口Token无效");
        }
    }
}
