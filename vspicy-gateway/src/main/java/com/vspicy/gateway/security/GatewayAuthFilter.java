package com.vspicy.gateway.security;

import com.vspicy.gateway.config.GatewayAuthProperties;
import com.vspicy.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {
    private final GatewayAuthProperties authProperties;
    private final JwtProperties jwtProperties;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public GatewayAuthFilter(GatewayAuthProperties authProperties, JwtProperties jwtProperties) {
        this.authProperties = authProperties;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!authProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String token = resolveToken(exchange);

        if ((token == null || token.isBlank()) && authProperties.isDevBypassEnabled()) {
            ServerHttpRequest mutated = appendIdentityHeaders(
                    request,
                    String.valueOf(authProperties.getDefaultUserId()),
                    authProperties.getDefaultUsername(),
                    authProperties.getDefaultRoles(),
                    authProperties.getDefaultPermissions()
            );
            return chain.filter(exchange.mutate().request(mutated).build());
        }

        if (token == null || token.isBlank()) {
            return writeJson(exchange, HttpStatus.UNAUTHORIZED, "未登录或 token 缺失");
        }

        try {
            Claims claims = parseClaims(token);
            String userId = resolveUserId(claims);
            String username = String.valueOf(claims.getOrDefault("username", ""));
            List<String> roles = claimAsList(claims.get("roles"));
            List<String> permissions = claimAsList(claims.get("permissions"));

            String requiredPermission = requiredPermission(path, request.getMethod().name());
            if (requiredPermission != null && !hasPermission(roles, permissions, requiredPermission)) {
                return writeJson(exchange, HttpStatus.FORBIDDEN, "无权限：" + requiredPermission);
            }

            ServerHttpRequest mutated = appendIdentityHeaders(request, userId, username, roles, permissions);
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception ex) {
            return writeJson(exchange, HttpStatus.UNAUTHORIZED, "token 无效或已过期");
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private String resolveToken(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        String accessToken = exchange.getRequest().getQueryParams().getFirst("access_token");
        if (accessToken != null && !accessToken.isBlank()) {
            return accessToken;
        }

        String token = exchange.getRequest().getQueryParams().getFirst("token");
        if (token != null && !token.isBlank()) {
            return token;
        }

        return null;
    }

    private boolean isWhitelisted(String path) {
        for (String pattern : authProperties.getWhitelist()) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private Claims parseClaims(String token) {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT secret 长度不能小于 32");
        }
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String resolveUserId(Claims claims) {
        Object userId = claims.get("userId");
        if (userId != null) {
            return String.valueOf(userId);
        }
        return claims.getSubject();
    }

    private List<String> claimAsList(Object value) {
        if (value == null) {
            return List.of();
        }

        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .filter(item -> !item.isBlank())
                    .toList();
        }

        return Arrays.stream(String.valueOf(value).split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String requiredPermission(String path, String method) {
        for (GatewayAuthProperties.PermissionRule rule : authProperties.getPermissionRules()) {
            if (rule.getPath() == null || rule.getPermission() == null) {
                continue;
            }

            boolean methodMatched = rule.getMethods() == null
                    || rule.getMethods().isEmpty()
                    || rule.getMethods().stream().anyMatch(item -> item.equalsIgnoreCase(method));

            if (methodMatched && matcher.match(rule.getPath(), path)) {
                return rule.getPermission();
            }
        }
        return null;
    }

    private boolean hasPermission(List<String> roles, List<String> permissions, String requiredPermission) {
        if (roles.contains("SUPER_ADMIN")) {
            return true;
        }
        if (permissions.contains("*")) {
            return true;
        }
        return permissions.contains(requiredPermission);
    }

    private ServerHttpRequest appendIdentityHeaders(
            ServerHttpRequest request,
            String userId,
            String username,
            List<String> roles,
            List<String> permissions
    ) {
        String roleHeader = String.join(",", roles);
        String permissionHeader = String.join(",", permissions);

        return request.mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-Username");
                    headers.remove("X-Roles");
                    headers.remove("X-Permissions");
                    headers.add("X-User-Id", userId == null ? "" : userId);
                    headers.add("X-Username", username == null ? "" : username);
                    headers.add("X-Roles", roleHeader);
                    headers.add("X-Permissions", permissionHeader);
                })
                .build();
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"code\":" + status.value() + ",\"message\":\"" + escape(message) + "\",\"data\":null}";
        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
