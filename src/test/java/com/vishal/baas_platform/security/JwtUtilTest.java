package com.vishal.baas_platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static JwtUtil jwtUtil(String secret) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", secret);
        util.init();
        return util;
    }

    private final JwtUtil jwtUtil = jwtUtil("test-secret-that-is-at-least-32-characters-long");

    @Test
    void tokenCarriesEmailAndUserId() {
        UUID userId = UUID.randomUUID();

        Claims claims = jwtUtil.extractClaims(jwtUtil.generateToken(userId, "dev@example.com"));

        assertThat(claims.getSubject()).isEqualTo("dev@example.com");
        assertThat(claims.get("userId", String.class)).isEqualTo(userId.toString());
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtUtil.generateToken(UUID.randomUUID(), "dev@example.com");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        assertThatThrownBy(() -> jwtUtil.extractClaims(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = jwtUtil("another-secret-that-is-at-least-32-characters")
                .generateToken(UUID.randomUUID(), "dev@example.com");

        assertThatThrownBy(() -> jwtUtil.extractClaims(token)).isInstanceOf(JwtException.class);
    }
}
