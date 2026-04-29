package com.vspicy.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vspicy.jwt")
public class JwtProperties {
    private String secret = "vspicy-dev-secret-please-change-32bytes-minimum";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
