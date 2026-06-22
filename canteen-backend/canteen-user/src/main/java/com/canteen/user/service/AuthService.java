package com.canteen.user.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.ResultCode;
import com.canteen.common.util.JwtTokenProvider;
import com.canteen.user.dto.*;
import com.canteen.user.entity.RefreshToken;
import com.canteen.user.entity.User;
import com.canteen.user.mapper.RefreshTokenMapper;
import com.canteen.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String BLACKLIST_PREFIX = "jwt:bl:";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(10);
    private static final String VERIFY_CODE = "123456";

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        // 检查手机号唯一�?
        Long phoneCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone, req.getPhone()));
        if (phoneCount > 0) {
            throw new BusinessException(ResultCode.USER_PHONE_EXISTS);
        }

        // 检查学工号唯一�?
        if (StrUtil.isNotBlank(req.getStudentNo())) {
            Long studentNoCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getStudentNo, req.getStudentNo()));
            if (studentNoCount > 0) {
                throw new BusinessException(ResultCode.USER_ALREADY_EXISTS, "学工号已注册");
            }
        }

        User user = new User();
        user.setPhone(req.getPhone());
        user.setStudentNo(req.getStudentNo());
        user.setPasswordHash(PASSWORD_ENCODER.encode(req.getPassword()));
        user.setNickname(StrUtil.blankToDefault(req.getNickname(), "用户" + req.getPhone().substring(7)));
        user.setStatus(1);
        user.setRole("user");
        userMapper.insert(user);

        log.info("User registered: id={}, phone={}", user.getId(), user.getPhone());
        return generateTokenPair(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, req.getPhone()));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        if ("password".equals(req.getLoginType())) {
            if (StrUtil.isBlank(req.getPassword()) || !PASSWORD_ENCODER.matches(req.getPassword(), user.getPasswordHash())) {
                throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
            }
        } else if ("sms".equals(req.getLoginType())) {
            if (!VERIFY_CODE.equals(req.getVerifyCode())) {
                throw new BusinessException(ResultCode.USER_VERIFY_CODE_ERROR);
            }
        } else {
            throw new BusinessException(-1, "不支持的登录方式");
        }

        log.info("User logged in: id={}, phone={}", user.getId(), user.getPhone());
        return generateTokenPair(user);
    }

    public TokenResponse refresh(RefreshRequest req) {
        String refreshToken = req.getRefreshToken();
        if (!jwtTokenProvider.validate(refreshToken)) {
            throw new BusinessException(ResultCode.USER_TOKEN_INVALID);
        }

        Claims claims = jwtTokenProvider.parseToken(refreshToken);
        String role = claims.get("role", String.class);
        if (!"refresh".equals(role)) {
            throw new BusinessException(ResultCode.USER_TOKEN_INVALID, "不是刷新令牌");
        }

        Long userId = Long.valueOf(claims.getSubject());

        // 检�?refresh_token 表中是否存在
        Long count = refreshTokenMapper.selectCount(
                new LambdaQueryWrapper<RefreshToken>()
                        .eq(RefreshToken::getUserId, userId)
                        .eq(RefreshToken::getTokenJti, claims.getId()));
        if (count == 0) {
            throw new BusinessException(ResultCode.USER_TOKEN_INVALID, "刷新令牌已吊销");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 删除�?refresh_token，颁发新�?
        refreshTokenMapper.delete(
                new LambdaQueryWrapper<RefreshToken>()
                        .eq(RefreshToken::getUserId, userId)
                        .eq(RefreshToken::getTokenJti, claims.getId()));

        return generateTokenPair(user);
    }

    public void logout(Long userId, String accessToken) {
        Claims claims = jwtTokenProvider.parseToken(accessToken);
        String jti = claims.getId();
        if (jti != null) {
            long ttl = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (ttl > 0) {
                stringRedisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", ttl, TimeUnit.MILLISECONDS);
            }
        }
        // 删除所�?refresh_token
        refreshTokenMapper.delete(
                new LambdaQueryWrapper<RefreshToken>().eq(RefreshToken::getUserId, userId));
        log.info("User logged out: id={}", userId);
    }

    private TokenResponse generateTokenPair(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole() != null ? user.getRole() : "user");
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 保存 refresh_token
        Claims refreshClaims = jwtTokenProvider.parseToken(refreshToken);
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenJti(refreshClaims.getId());
        rt.setExpiresAt(LocalDateTime.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpiration() / 1000));
        refreshTokenMapper.insert(rt);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .userId(user.getId())
                .build();
    }
}
