package com.canteen.user.controller;

import com.canteen.common.result.Result;
import com.canteen.user.dto.*;
import com.canteen.user.service.AuthService;
import com.canteen.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/register")
    public Result<TokenResponse> register(@Valid @RequestBody RegisterRequest req) {
        return Result.success(authService.register(req));
    }

    @PostMapping("/auth/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(authService.login(req));
    }

    @PostMapping("/auth/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return Result.success(authService.refresh(req));
    }

    @PostMapping("/auth/logout")
    public Result<Void> logout(@RequestHeader("X-User-Id") Long userId,
                               @RequestHeader("Authorization") String authorization) {
        String token = authorization.replace("Bearer ", "");
        authService.logout(userId, token);
        return Result.success();
    }
}
