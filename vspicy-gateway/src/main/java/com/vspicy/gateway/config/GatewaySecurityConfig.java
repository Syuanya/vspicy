package com.vspicy.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({GatewayAuthProperties.class, JwtProperties.class})
public class GatewaySecurityConfig {
}
