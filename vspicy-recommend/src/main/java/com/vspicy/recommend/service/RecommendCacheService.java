package com.vspicy.recommend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vspicy.recommend.config.RecommendCacheProperties;
import com.vspicy.recommend.dto.RecommendCacheStatsResponse;
import com.vspicy.recommend.dto.RecommendationItem;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
public class RecommendCacheService {
    private static final String PREFIX = "vspicy:recommend:";
    private static final String STALE_PREFIX = "vspicy:recommend:stale:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RecommendCacheProperties properties;

    public RecommendCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RecommendCacheProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.isEnabled();
    }

    public String feedKey(Long userId, Integer limit) {
        return PREFIX + "feed:user:" + userId + ":limit:" + limit;
    }

    public String hotKey(String targetType, Integer limit) {
        String type = targetType == null || targetType.isBlank() ? "ALL" : targetType;
        return PREFIX + "hot:type:" + type + ":limit:" + limit;
    }

    public String similarKey(Long targetId, String targetType, Integer limit) {
        return PREFIX + "similar:" + targetType + ":" + targetId + ":limit:" + limit;
    }

    public List<RecommendationItem> getFresh(String key) {
        return get(key);
    }

    public List<RecommendationItem> getStale(String key) {
        return get(staleKey(key));
    }

    public void put(String key, List<RecommendationItem> items, long freshTtlSeconds) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(items);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(freshTtlSeconds));
            redisTemplate.opsForValue().set(staleKey(key), json, Duration.ofSeconds(properties.getStaleTtlSeconds()));
        } catch (Exception ex) {
            System.err.println("推荐缓存写入失败: " + ex.getMessage());
        }
    }

    public RecommendCacheStatsResponse stats() {
        if (!properties.isEnabled()) {
            return new RecommendCacheStatsResponse(
                    false,
                    0,
                    0,
                    properties.getFeedTtlSeconds(),
                    properties.getHotTtlSeconds(),
                    properties.getSimilarTtlSeconds(),
                    properties.getStaleTtlSeconds()
            );
        }

        try {
            long fresh = size(PREFIX + "*");
            long stale = size(STALE_PREFIX + "*");
            return new RecommendCacheStatsResponse(
                    true,
                    fresh,
                    stale,
                    properties.getFeedTtlSeconds(),
                    properties.getHotTtlSeconds(),
                    properties.getSimilarTtlSeconds(),
                    properties.getStaleTtlSeconds()
            );
        } catch (Exception ex) {
            System.err.println("推荐缓存统计失败，已降级: " + ex.getMessage());
            return new RecommendCacheStatsResponse(
                    true,
                    -1,
                    -1,
                    properties.getFeedTtlSeconds(),
                    properties.getHotTtlSeconds(),
                    properties.getSimilarTtlSeconds(),
                    properties.getStaleTtlSeconds()
            );
        }
    }

    public long clearAll() {
        if (!properties.isEnabled()) {
            return 0;
        }

        try {
            Set<String> keys = redisTemplate.keys(PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            redisTemplate.delete(keys);
            return keys.size();
        } catch (Exception ex) {
            System.err.println("推荐缓存清理失败，已降级: " + ex.getMessage());
            return -1;
        }
    }

    public long feedTtl() {
        return properties.getFeedTtlSeconds();
    }

    public long hotTtl() {
        return properties.getHotTtlSeconds();
    }

    public long similarTtl() {
        return properties.getSimilarTtlSeconds();
    }

    private List<RecommendationItem> get(String key) {
        if (!properties.isEnabled()) {
            return null;
        }

        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<List<RecommendationItem>>() {});
        } catch (Exception ex) {
            System.err.println("推荐缓存读取失败: " + ex.getMessage());
            return null;
        }
    }

    private String staleKey(String key) {
        if (key.startsWith(PREFIX)) {
            return STALE_PREFIX + key.substring(PREFIX.length());
        }
        return STALE_PREFIX + key;
    }

    private long size(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        return keys == null ? 0 : keys.size();
    }
}
