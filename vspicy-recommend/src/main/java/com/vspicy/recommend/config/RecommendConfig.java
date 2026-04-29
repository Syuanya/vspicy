package com.vspicy.recommend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RecommendCacheProperties.class)
public class RecommendConfig {
}
