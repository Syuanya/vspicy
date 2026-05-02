package com.vspicy.auth.service;

import com.vspicy.auth.config.AuthJwtProperties;
import com.vspicy.auth.dto.CurrentUserResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class AuthJwtTokenService {
    private final AuthJwtProperties properties;

    public AuthJwtTokenService(AuthJwtProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(CurrentUserResponse user) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(properties.getAccessTokenMinutes() * 60);

        // accessToken 只保留身份和角色，不再写入 permissions。
        // roles 体积很小，网关可继续识别 SUPER_ADMIN；permissions 过多会导致 Authorization 请求头过大。
        return Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .claims(Map.of(
                        "userId", user.userId(),
                        "username", user.username(),
                        "roles", safeList(user.roles()),
                        "tokenType", "access"
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey())
                .compact();
    }

    public String createRefreshToken(CurrentUserResponse user) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(properties.getRefreshTokenDays() * 24 * 3600);

        return Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .claims(Map.of(
                        "userId", user.userId(),
                        "username", user.username(),
                        "tokenType", "refresh"
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey())
                .compact();
    }

    public long accessTokenExpiresInSeconds() {
        return properties.getAccessTokenMinutes() * 60;
    }

    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object userId = claims.get("userId");
        if (userId != null) {
            return Long.valueOf(String.valueOf(userId));
        }
        return Long.valueOf(claims.getSubject());
    }

    private List<String> safeList(List<String> value) {
        return value == null ? List.of() : value;
    }

    private SecretKey secretKey() {
        String secret = properties.getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT secret 长度不能小于 32");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
