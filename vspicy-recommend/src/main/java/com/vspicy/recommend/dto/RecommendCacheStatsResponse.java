package com.vspicy.recommend.dto;

public record RecommendCacheStatsResponse(
        boolean enabled,
        long freshKeyCount,
        long staleKeyCount,
        long feedTtlSeconds,
        long hotTtlSeconds,
        long similarTtlSeconds,
        long staleTtlSeconds
) {
}
