package com.canteen.common.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        jwtTokenProvider.setSecret("canteen-smart-canteen-system-jwt-secret-key-must-be-at-least-256-bits-long");
        jwtTokenProvider.setAccessTokenExpiration(900000L);   // 15 min
        jwtTokenProvider.setRefreshTokenExpiration(604800000L); // 7 days
    }

    @Test
    @DisplayName("生成并验证 AccessToken")
    void testGenerateAndValidateAccessToken() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user");
        assertNotNull(token);
        assertTrue(jwtTokenProvider.validate(token));

        Claims claims = jwtTokenProvider.parseToken(token);
        assertEquals("1", claims.getSubject());
        assertEquals("user", claims.get("role", String.class));
        assertNotNull(claims.getId()); // jti
    }

    @Test
    @DisplayName("生成并验证 RefreshToken")
    void testGenerateAndValidateRefreshToken() {
        String token = jwtTokenProvider.generateRefreshToken(1L);
        assertNotNull(token);
        assertTrue(jwtTokenProvider.validate(token));

        Claims claims = jwtTokenProvider.parseToken(token);
        assertEquals("1", claims.getSubject());
        assertEquals("refresh", claims.get("role", String.class));
    }

    @Test
    @DisplayName("无效 Token 验证失败")
    void testInvalidToken() {
        assertFalse(jwtTokenProvider.validate("invalid-token"));
        assertFalse(jwtTokenProvider.validate(""));
        assertFalse(jwtTokenProvider.validate(null));
    }

    @Test
    @DisplayName("过期 Token 验证失败")
    void testExpiredToken() {
        jwtTokenProvider.setAccessTokenExpiration(1L); // 1ms
        String token = jwtTokenProvider.generateAccessToken(1L, "user");

        // 等待过期
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        assertFalse(jwtTokenProvider.validate(token));
    }
}
