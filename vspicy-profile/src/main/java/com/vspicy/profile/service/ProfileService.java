package com.vspicy.profile.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.profile.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProfileService {
    private final JdbcTemplate jdbcTemplate;

    public ProfileService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ProfileBuildResponse rebuildUserProfile(Long userId) {
        if (userId == null) {
            throw new BizException("userId 不能为空");
        }

        List<Map<String, Object>> behaviors = jdbcTemplate.queryForList("""
                SELECT user_id, target_id, target_type, action_type, created_at
                FROM user_behavior_log
                WHERE user_id = ?
                  AND created_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)
                ORDER BY id DESC
                LIMIT 2000
                """, userId);

        Map<Long, TagScore> scoreMap = new HashMap<>();

        for (Map<String, Object> behavior : behaviors) {
            Long targetId = ((Number) behavior.get("target_id")).longValue();
            String targetType = String.valueOf(behavior.get("target_type"));
            String actionType = String.valueOf(behavior.get("action_type"));
            double weight = behaviorWeight(actionType);

            List<Map<String, Object>> tags = jdbcTemplate.queryForList("""
                    SELECT t.id, t.name
                    FROM content_tag_relation r
                    JOIN tag t ON t.id = r.tag_id
                    WHERE r.content_id = ?
                      AND r.content_type = ?
                      AND t.status = 1
                    """, targetId, targetType);

            for (Map<String, Object> tag : tags) {
                Long tagId = ((Number) tag.get("id")).longValue();
                String tagName = String.valueOf(tag.get("name"));
                TagScore tagScore = scoreMap.computeIfAbsent(tagId, k -> new TagScore(tagId, tagName));
                tagScore.score += weight;
            }
        }

        jdbcTemplate.update("DELETE FROM user_interest_profile WHERE user_id = ?", userId);

        for (TagScore item : scoreMap.values()) {
            jdbcTemplate.update("""
                    INSERT INTO user_interest_profile(user_id, tag_id, tag_name, score, source, last_behavior_at)
                    VALUES (?, ?, ?, ?, 'BEHAVIOR', NOW())
                    """, userId, item.tagId, item.tagName, item.score);
        }

        jdbcTemplate.update("""
                INSERT INTO user_profile_build_log(user_id, build_type, status, message)
                VALUES (?, 'MANUAL', 'SUCCESS', ?)
                """, userId, "行为数=" + behaviors.size() + ", 标签数=" + scoreMap.size());

        return new ProfileBuildResponse(userId, behaviors.size(), scoreMap.size(), "用户画像构建完成");
    }

    public List<UserInterestItem> userInterests(Long userId, Integer limit) {
        if (userId == null) {
            throw new BizException("userId 不能为空");
        }
        int safeLimit = normalizeLimit(limit, 30, 100);

        return jdbcTemplate.query("""
                SELECT user_id, tag_id, tag_name, score, source, last_behavior_at
                FROM user_interest_profile
                WHERE user_id = ?
                ORDER BY score DESC
                LIMIT ?
                """, (rs, rowNum) -> new UserInterestItem(
                rs.getLong("user_id"),
                rs.getLong("tag_id"),
                rs.getString("tag_name"),
                rs.getDouble("score"),
                rs.getString("source"),
                rs.getString("last_behavior_at")
        ), userId, safeLimit);
    }

    @Transactional
    public ContentProfileResponse bindContentTags(ContentTagCommand command) {
        if (command == null || command.contentId() == null || command.contentType() == null || command.contentType().isBlank()) {
            throw new BizException("内容标签参数不完整");
        }
        if (command.tagNames() == null || command.tagNames().isEmpty()) {
            throw new BizException("标签不能为空");
        }

        jdbcTemplate.update("DELETE FROM content_tag_relation WHERE content_id = ? AND content_type = ?",
                command.contentId(), command.contentType());

        for (String rawName : command.tagNames()) {
            if (rawName == null || rawName.isBlank()) {
                continue;
            }
            String tagName = rawName.trim();

            Long tagId = findOrCreateTag(tagName);
            jdbcTemplate.update("""
                    INSERT IGNORE INTO content_tag_relation(content_id, content_type, tag_id)
                    VALUES (?, ?, ?)
                    """, command.contentId(), command.contentType(), tagId);

            jdbcTemplate.update("UPDATE tag SET use_count = use_count + 1 WHERE id = ?", tagId);
        }

        rebuildContentProfile(command.contentId(), command.contentType());
        return contentProfile(command.contentType(), command.contentId());
    }

    public ContentProfileResponse contentProfile(String targetType, Long targetId) {
        if (targetId == null || targetType == null || targetType.isBlank()) {
            throw new BizException("内容画像参数不完整");
        }

        rebuildContentProfile(targetId, targetType);

        List<String> tags = jdbcTemplate.query("""
                SELECT t.name
                FROM content_tag_relation r
                JOIN tag t ON t.id = r.tag_id
                WHERE r.content_id = ?
                  AND r.content_type = ?
                ORDER BY t.id DESC
                """, (rs, rowNum) -> rs.getString("name"), targetId, targetType);

        Map<String, Object> content = loadContent(targetId, targetType);
        double hotScore = calcHotScore(targetId, targetType);
        double qualityScore = Math.min(100.0, hotScore * 1.2 + tags.size() * 5.0);

        return new ContentProfileResponse(
                targetId,
                targetType,
                String.valueOf(content.getOrDefault("title", targetType + "-" + targetId)),
                String.valueOf(content.getOrDefault("status", "UNKNOWN")),
                tags,
                Math.round(hotScore * 100.0) / 100.0,
                Math.round(qualityScore * 100.0) / 100.0
        );
    }

    public List<HotTagResponse> hotTags(Integer limit) {
        int safeLimit = normalizeLimit(limit, 30, 100);

        return jdbcTemplate.query("""
                SELECT
                  t.id AS tag_id,
                  t.name AS tag_name,
                  t.use_count,
                  COUNT(DISTINCT p.user_id) AS user_count,
                  COALESCE(SUM(p.score), 0) AS total_score
                FROM tag t
                LEFT JOIN user_interest_profile p ON p.tag_id = t.id
                WHERE t.status = 1
                GROUP BY t.id, t.name, t.use_count
                ORDER BY total_score DESC, use_count DESC
                LIMIT ?
                """, (rs, rowNum) -> new HotTagResponse(
                rs.getLong("tag_id"),
                rs.getString("tag_name"),
                rs.getLong("use_count"),
                rs.getLong("user_count"),
                rs.getDouble("total_score")
        ), safeLimit);
    }

    private Long findOrCreateTag(String tagName) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM tag WHERE name = ? AND tag_type = 'CONTENT' LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                tagName
        );

        if (!ids.isEmpty()) {
            return ids.get(0);
        }

        jdbcTemplate.update("""
                INSERT INTO tag(name, tag_type, use_count, status)
                VALUES (?, 'CONTENT', 0, 1)
                """, tagName);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM tag WHERE name = ? AND tag_type = 'CONTENT' LIMIT 1",
                Long.class,
                tagName
        );
    }

    private void rebuildContentProfile(Long contentId, String contentType) {
        Map<String, Object> content = loadContent(contentId, contentType);
        String title = String.valueOf(content.getOrDefault("title", contentType + "-" + contentId));
        String status = String.valueOf(content.getOrDefault("status", "UNKNOWN"));

        List<String> tags = jdbcTemplate.query("""
                SELECT t.name
                FROM content_tag_relation r
                JOIN tag t ON t.id = r.tag_id
                WHERE r.content_id = ?
                  AND r.content_type = ?
                ORDER BY t.id DESC
                """, (rs, rowNum) -> rs.getString("name"), contentId, contentType);

        double hotScore = calcHotScore(contentId, contentType);
        double qualityScore = Math.min(100.0, hotScore * 1.2 + tags.size() * 5.0);
        String tagNames = String.join(",", tags);

        jdbcTemplate.update("""
                INSERT INTO content_profile(content_id, content_type, title, status, tag_names, hot_score, quality_score)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  title = VALUES(title),
                  status = VALUES(status),
                  tag_names = VALUES(tag_names),
                  hot_score = VALUES(hot_score),
                  quality_score = VALUES(quality_score),
                  updated_at = CURRENT_TIMESTAMP
                """, contentId, contentType, title, status, tagNames, hotScore, qualityScore);
    }

    private Map<String, Object> loadContent(Long contentId, String contentType) {
        String table;
        if ("ARTICLE".equalsIgnoreCase(contentType)) {
            table = "article";
        } else if ("VIDEO".equalsIgnoreCase(contentType)) {
            table = "video";
        } else {
            return Map.of("title", contentType + "-" + contentId, "status", "UNKNOWN");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT title, status FROM " + table + " WHERE id = ? LIMIT 1",
                contentId
        );
        return rows.isEmpty() ? Map.of("title", contentType + "-" + contentId, "status", "UNKNOWN") : rows.get(0);
    }

    private double calcHotScore(Long targetId, String targetType) {
        Map<String, Object> behavior = jdbcTemplate.queryForMap("""
                SELECT
                  SUM(CASE WHEN action_type = 'VIEW' THEN 1 ELSE 0 END) AS view_count,
                  SUM(CASE WHEN action_type = 'PLAY' THEN 1 ELSE 0 END) AS play_count
                FROM user_behavior_log
                WHERE target_id = ?
                  AND target_type = ?
                  AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                """, targetId, targetType);

        long viewCount = numberValue(behavior.get("view_count"));
        long playCount = numberValue(behavior.get("play_count"));

        long likeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM like_record
                WHERE target_id = ? AND target_type = ? AND status = 1
                """, Long.class, targetId, targetType);

        long favoriteCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM favorite_record
                WHERE target_id = ? AND target_type = ? AND status = 1
                """, Long.class, targetId, targetType);

        long commentCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM comment
                WHERE content_id = ? AND content_type = ? AND deleted = 0
                """, Long.class, targetId, targetType);

        return viewCount * 1.0 + playCount * 2.0 + likeCount * 5.0 + favoriteCount * 8.0 + commentCount * 6.0;
    }

    private double behaviorWeight(String actionType) {
        return switch (actionType) {
            case "VIEW" -> 1.0;
            case "PLAY" -> 2.0;
            case "LIKE" -> 5.0;
            case "FAVORITE" -> 8.0;
            case "COMMENT" -> 6.0;
            default -> 0.5;
        };
    }

    private long numberValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private int normalizeLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }

    private static class TagScore {
        Long tagId;
        String tagName;
        double score;

        TagScore(Long tagId, String tagName) {
            this.tagId = tagId;
            this.tagName = tagName;
        }
    }
}
