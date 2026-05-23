package com.canteen.pickup.controller;

import com.canteen.common.result.Result;
import com.canteen.pickup.dto.*;
import com.canteen.pickup.service.PickupQueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PickupController {

    private final PickupQueueService pickupQueueService;

    @GetMapping("/queues/{counterId}")
    public Result<QueueScreenVO> getScreenData(@PathVariable String counterId) {
        return Result.success(pickupQueueService.getScreenData(counterId));
    }

    @PostMapping("/queues/{counterId}/call")
    public Result<QueueEntry> call(@PathVariable String counterId) {
        return Result.success(pickupQueueService.call(counterId));
    }

    @PostMapping("/pickups/verify")
    public Result<Void> verify(@Valid @RequestBody VerifyRequest req) {
        pickupQueueService.verify(req);
        return Result.success();
    }
}
