package com.canteen.user.controller;

import com.canteen.common.result.Result;
import com.canteen.user.dto.UserUpdateRequest;
import com.canteen.user.dto.UserVO;
import com.canteen.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public Result<UserVO> getMe(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(userProfileService.getMe(userId));
    }

    @PutMapping("/me")
    public Result<UserVO> updateMe(@RequestHeader("X-User-Id") Long userId,
                                   @Valid @RequestBody UserUpdateRequest req) {
        return Result.success(userProfileService.updateMe(userId, req));
    }
}
