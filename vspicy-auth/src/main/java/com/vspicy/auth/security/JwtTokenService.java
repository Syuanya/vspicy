package com.vspicy.auth.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtTokenService {
    private static final String SECRET = "vspicy-local-dev-jwt-secret-key-at-least-32-bytes";
    private static final long EXPIRES_SECONDS = 7200L;

    public String issueToken(Long userId, String username) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(Map.of("username", username))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(EXPIRES_SECONDS)))
                .signWith(key)
                .compact();
    }

    public long expiresSeconds() {
        return EXPIRES_SECONDS;
    }
}
