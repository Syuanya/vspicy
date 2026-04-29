package com.vspicy.member.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vspicy.member.config.MemberCacheProperties;
import com.vspicy.member.dto.UserMembershipView;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class MemberCacheService {
    private static final String KEY_PREFIX = "vspicy:member:user:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MemberCacheProperties properties;

    public MemberCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MemberCacheProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<UserMembershipView> getMembership(Long userId) {
        if (!properties.isEnabled() || userId == null) {
            return Optional.empty();
        }

        try {
            String json = redisTemplate.opsForValue().get(key(userId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, UserMembershipView.class));
        } catch (Exception ex) {
            System.err.println("读取会员缓存失败，降级查库: userId=" + userId + ", error=" + ex.getMessage());
            return Optional.empty();
        }
    }

    public void putMembership(Long userId, UserMembershipView view) {
        if (!properties.isEnabled() || userId == null || view == null) {
            return;
        }

        try {
            long ttl = properties.getMembershipTtlSeconds() <= 0 ? 1800 : properties.getMembershipTtlSeconds();
            redisTemplate.opsForValue().set(
                    key(userId),
                    objectMapper.writeValueAsString(view),
                    Duration.ofSeconds(ttl)
            );
        } catch (Exception ex) {
            System.err.println("写入会员缓存失败: userId=" + userId + ", error=" + ex.getMessage());
        }
    }

    public void evictMembership(Long userId) {
        if (userId == null) {
            return;
        }

        try {
            redisTemplate.delete(key(userId));
        } catch (Exception ex) {
            System.err.println("删除会员缓存失败: userId=" + userId + ", error=" + ex.getMessage());
        }
    }

    public String key(Long userId) {
        return KEY_PREFIX + userId + ":membership";
    }
}
