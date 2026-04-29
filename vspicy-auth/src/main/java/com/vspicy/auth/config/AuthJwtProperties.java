package com.vspicy.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vspicy.jwt")
public class AuthJwtProperties {
    private String secret = "vspicy-dev-secret-please-change-32bytes-minimum";
    private long accessTokenMinutes = 720;
    private long refreshTokenDays = 7;

    public String getSecret() { return secret; }
    public long getAccessTokenMinutes() { return accessTokenMinutes; }
    public long getRefreshTokenDays() { return refreshTokenDays; }

    public void setSecret(String secret) { this.secret = secret; }
    public void setAccessTokenMinutes(long accessTokenMinutes) { this.accessTokenMinutes = accessTokenMinutes; }
    public void setRefreshTokenDays(long refreshTokenDays) { this.refreshTokenDays = refreshTokenDays; }
}
