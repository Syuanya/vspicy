package com.vspicy.recommend.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.recommend.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RecommendService {
    private final JdbcTemplate jdbcTemplate;
    private final RecommendCacheService cacheService;

    public RecommendService(JdbcTemplate jdbcTemplate, RecommendCacheService cacheService) {
        this.jdbcTemplate = jdbcTemplate;
        this.cacheService = cacheService;
    }

    public List<RecommendationItem> feed(Long userId, Integer limit) {
        if (userId == null) {
            userId = 1L;
        }
        int safeLimit = normalizeLimit(limit, 20, 100);
        String cacheKey = cacheService.feedKey(userId, safeLimit);

        List<RecommendationItem> fresh = cacheService.getFresh(cacheKey);
        if (fresh != null) {
            return fresh;
        }

        try {
            List<RecommendationItem> result = buildFeed(userId, safeLimit);
            cacheService.put(cacheKey, result, cacheService.feedTtl());
            return result;
        } catch (Exception ex) {
            List<RecommendationItem> stale = cacheService.getStale(cacheKey);
            if (stale != null) {
                System.err.println("推荐 Feed 使用 stale cache 降级: " + ex.getMessage());
                return stale;
            }
            throw ex;
        }
    }

    public List<RecommendationItem> hot(String targetType, Integer limit) {
        int safeLimit = normalizeLimit(limit, 20, 100);
        String cacheKey = cacheService.hotKey(targetType, safeLimit);

        List<RecommendationItem> fresh = cacheService.getFresh(cacheKey);
        if (fresh != null) {
            return fresh;
        }

        try {
            List<RecommendationItem> result = queryHotCandidates(targetType, safeLimit)
                    .stream()
                    .map(Candidate::toItem)
                    .toList();
            cacheService.put(cacheKey, result, cacheService.hotTtl());
            return result;
        } catch (Exception ex) {
            List<RecommendationItem> stale = cacheService.getStale(cacheKey);
            if (stale != null) {
                System.err.println("热门推荐使用 stale cache 降级: " + ex.getMessage());
                return stale;
            }
            throw ex;
        }
    }

    public List<RecommendationItem> personalized(Long userId, Integer limit) {
        return feed(userId, limit);
    }

    public List<RecommendationItem> similar(Long targetId, String targetType, Integer limit) {
        if (targetId == null || targetType == null || targetType.isBlank()) {
            throw new BizException("相似推荐参数不完整");
        }

        int safeLimit = normalizeLimit(limit, 12, 50);
        String cacheKey = cacheService.similarKey(targetId, targetType, safeLimit);

        List<RecommendationItem> fresh = cacheService.getFresh(cacheKey);
        if (fresh != null) {
            return fresh;
        }

        try {
            List<RecommendationItem> result = buildSimilar(targetId, targetType, safeLimit);
            cacheService.put(cacheKey, result, cacheService.similarTtl());
            return result;
        } catch (Exception ex) {
            List<RecommendationItem> stale = cacheService.getStale(cacheKey);
            if (stale != null) {
                System.err.println("相似推荐使用 stale cache 降级: " + ex.getMessage());
                return stale;
            }
            throw ex;
        }
    }

    public RecommendationDebugResponse debugUser(Long userId) {
        if (userId == null) {
            userId = 1L;
        }

        List<Map<String, Object>> interests = jdbcTemplate.queryForList("""
                SELECT tag_id, tag_name, score
                FROM user_interest_profile
                WHERE user_id = ?
                ORDER BY score DESC
                LIMIT 20
                """, userId);

        List<Map<String, Object>> behaviors = jdbcTemplate.queryForList("""
                SELECT target_id, target_type, action_type, created_at
                FROM user_behavior_log
                WHERE user_id = ?
                ORDER BY id DESC
                LIMIT 30
                """, userId);

        List<Map<String, Object>> exposures = jdbcTemplate.queryForList("""
                SELECT target_id, target_type, scene, score, created_at
                FROM recommend_exposure_log
                WHERE user_id = ?
                ORDER BY id DESC
                LIMIT 30
                """, userId);

        return new RecommendationDebugResponse(userId, interests, behaviors, exposures, feed(userId, 20));
    }

    public RecommendCacheStatsResponse cacheStats() {
        return cacheService.stats();
    }

    public long clearCache() {
        return cacheService.clearAll();
    }

    public void exposure(RecommendationExposureCommand command) {
        if (command == null || command.targetId() == null || command.targetType() == null) {
            throw new BizException("曝光参数不完整");
        }

        jdbcTemplate.update("""
                INSERT INTO recommend_exposure_log(user_id, target_id, target_type, scene, rank_no, score, request_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                command.userId() == null ? 1L : command.userId(),
                command.targetId(),
                command.targetType(),
                command.scene() == null || command.scene().isBlank() ? "HOME" : command.scene(),
                command.rankNo(),
                command.score() == null ? 0 : command.score(),
                command.requestId()
        );
    }

    public void feedback(RecommendationFeedbackCommand command) {
        if (command == null || command.targetId() == null || command.targetType() == null || command.feedbackType() == null) {
            throw new BizException("反馈参数不完整");
        }

        jdbcTemplate.update("""
                INSERT INTO recommend_feedback_log(user_id, target_id, target_type, scene, feedback_type, request_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                command.userId() == null ? 1L : command.userId(),
                command.targetId(),
                command.targetType(),
                command.scene() == null || command.scene().isBlank() ? "HOME" : command.scene(),
                command.feedbackType(),
                command.requestId()
        );
    }

    private List<RecommendationItem> buildFeed(Long userId, int safeLimit) {
        String requestId = "feed-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Candidate> candidates = new LinkedHashMap<>();

        recallByProfileTags(userId, candidates);
        recallByTypeBoost(userId, candidates);
        recallByHot(candidates, safeLimit * 3);

        List<Long> recentExposures = recentlyExposedIds(userId, "HOME");
        for (Candidate candidate : candidates.values()) {
            if (recentExposures.contains(candidate.targetId)) {
                candidate.score -= 20.0;
                candidate.reason = candidate.reason + "；近期已曝光降权";
            }
        }

        List<RecommendationItem> result = candidates.values().stream()
                .sorted(Comparator.comparing(Candidate::score).reversed())
                .limit(safeLimit)
                .map(Candidate::toItem)
                .toList();

        saveRecallLogs(userId, requestId, result);
        return result;
    }

    private List<RecommendationItem> buildSimilar(Long targetId, String targetType, int safeLimit) {
        Map<String, Candidate> candidates = new LinkedHashMap<>();

        List<Map<String, Object>> tags = jdbcTemplate.queryForList("""
                SELECT tag_id
                FROM content_tag_relation
                WHERE content_id = ?
                  AND content_type = ?
                """, targetId, targetType);

        for (Map<String, Object> tag : tags) {
            Long tagId = ((Number) tag.get("tag_id")).longValue();
            List<Candidate> tagCandidates = queryByTag(tagId, 30, "SIMILAR_TAG", 20.0, "相同标签内容");
            for (Candidate candidate : tagCandidates) {
                if (!Objects.equals(candidate.targetId, targetId) || !candidate.targetType.equals(targetType)) {
                    mergeCandidate(candidates, candidate);
                }
            }
        }

        if (candidates.isEmpty()) {
            return hot(targetType, safeLimit + 1).stream()
                    .filter(item -> !Objects.equals(item.targetId(), targetId))
                    .limit(safeLimit)
                    .map(item -> copyWithScore(item, item.score(), "同类型热门内容", "HOT"))
                    .toList();
        }

        return candidates.values().stream()
                .sorted(Comparator.comparing(Candidate::score).reversed())
                .limit(safeLimit)
                .map(Candidate::toItem)
                .toList();
    }

    private void recallByProfileTags(Long userId, Map<String, Candidate> candidates) {
        List<Map<String, Object>> interests = jdbcTemplate.queryForList("""
                SELECT tag_id, tag_name, score
                FROM user_interest_profile
                WHERE user_id = ?
                ORDER BY score DESC
                LIMIT 10
                """, userId);

        for (Map<String, Object> interest : interests) {
            Long tagId = ((Number) interest.get("tag_id")).longValue();
            String tagName = String.valueOf(interest.get("tag_name"));
            double interestScore = ((Number) interest.get("score")).doubleValue();

            List<Candidate> tagCandidates = queryByTag(
                    tagId,
                    30,
                    "PROFILE_TAG",
                    Math.min(interestScore * 2.0, 80.0),
                    "命中兴趣标签：" + tagName
            );

            for (Candidate candidate : tagCandidates) {
                mergeCandidate(candidates, candidate);
            }
        }
    }

    private void recallByTypeBoost(Long userId, Map<String, Candidate> candidates) {
        Map<String, Double> typeBoost = loadTypeBoost(userId);

        for (Map.Entry<String, Double> entry : typeBoost.entrySet()) {
            if (entry.getValue() <= 1.05) {
                continue;
            }

            List<Candidate> rows = queryHotCandidates(entry.getKey(), 30);
            for (Candidate candidate : rows) {
                candidate.score = candidate.score * entry.getValue();
                candidate.recallSource = "TYPE_BOOST";
                candidate.reason = "基于你的" + entry.getKey() + "偏好召回";
                mergeCandidate(candidates, candidate);
            }
        }
    }

    private void recallByHot(Map<String, Candidate> candidates, int limit) {
        for (Candidate candidate : queryHotCandidates(null, limit)) {
            mergeCandidate(candidates, candidate);
        }
    }

    private List<Candidate> queryByTag(Long tagId, int limit, String source, double recallScore, String reason) {
        return jdbcTemplate.query("""
                SELECT
                  r.content_id AS target_id,
                  r.content_type AS target_type,
                  COALESCE(v.title, a.title, CONCAT(r.content_type, '-', r.content_id)) AS title,
                  COALESCE(v.status, a.status, 'UNKNOWN') AS status,
                  COALESCE(cp.hot_score, 0) AS hot_score,
                  COALESCE(cp.quality_score, 0) AS quality_score,
                  COALESCE(l.like_count, 0) AS like_count,
                  COALESCE(f.favorite_count, 0) AS favorite_count,
                  COALESCE(c.comment_count, 0) AS comment_count
                FROM content_tag_relation r
                LEFT JOIN content_profile cp ON cp.content_id = r.content_id AND cp.content_type = r.content_type
                LEFT JOIN article a ON r.content_type = 'ARTICLE' AND a.id = r.content_id
                LEFT JOIN video v ON r.content_type = 'VIDEO' AND v.id = r.content_id
                LEFT JOIN (
                  SELECT target_id, target_type, COUNT(*) AS like_count
                  FROM like_record
                  WHERE status = 1
                  GROUP BY target_id, target_type
                ) l ON l.target_id = r.content_id AND l.target_type = r.content_type
                LEFT JOIN (
                  SELECT target_id, target_type, COUNT(*) AS favorite_count
                  FROM favorite_record
                  WHERE status = 1
                  GROUP BY target_id, target_type
                ) f ON f.target_id = r.content_id AND f.target_type = r.content_type
                LEFT JOIN (
                  SELECT content_id AS target_id, content_type AS target_type, COUNT(*) AS comment_count
                  FROM comment
                  WHERE deleted = 0
                  GROUP BY content_id, content_type
                ) c ON c.target_id = r.content_id AND c.target_type = r.content_type
                WHERE r.tag_id = ?
                ORDER BY COALESCE(cp.hot_score, 0) DESC, r.id DESC
                LIMIT ?
                """, (rs, rowNum) -> {
            Candidate candidate = new Candidate();
            candidate.targetId = rs.getLong("target_id");
            candidate.targetType = rs.getString("target_type");
            candidate.title = rs.getString("title");
            candidate.status = rs.getString("status");
            candidate.viewCount = 0;
            candidate.playCount = 0;
            candidate.likeCount = rs.getLong("like_count");
            candidate.favoriteCount = rs.getLong("favorite_count");
            candidate.commentCount = rs.getLong("comment_count");
            candidate.score = recallScore + rs.getDouble("hot_score") + rs.getDouble("quality_score") * 0.3;
            candidate.reason = reason;
            candidate.recallSource = source;
            return candidate;
        }, tagId, limit);
    }

    private List<Candidate> queryHotCandidates(String targetType, int limit) {
        List<Object> params = new ArrayList<>();

        String typeCondition = "";
        if (targetType != null && !targetType.isBlank()) {
            typeCondition = " AND u.target_type = ? ";
            params.add(targetType);
        }
        params.add(limit);

        String sql = """
                SELECT
                  s.target_id,
                  s.target_type,
                  COALESCE(v.title, a.title, CONCAT(s.target_type, '-', s.target_id)) AS title,
                  COALESCE(v.status, a.status, 'UNKNOWN') AS status,
                  s.view_count,
                  s.play_count,
                  COALESCE(l.like_count, 0) AS like_count,
                  COALESCE(f.favorite_count, 0) AS favorite_count,
                  COALESCE(c.comment_count, 0) AS comment_count,
                  (
                    s.view_count * 1
                    + s.play_count * 2
                    + COALESCE(l.like_count, 0) * 5
                    + COALESCE(f.favorite_count, 0) * 8
                    + COALESCE(c.comment_count, 0) * 6
                  ) AS score
                FROM (
                  SELECT
                    u.target_id,
                    u.target_type,
                    SUM(CASE WHEN u.action_type = 'VIEW' THEN 1 ELSE 0 END) AS view_count,
                    SUM(CASE WHEN u.action_type = 'PLAY' THEN 1 ELSE 0 END) AS play_count
                  FROM user_behavior_log u
                  WHERE u.created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                """ + typeCondition + """
                  GROUP BY u.target_id, u.target_type
                ) s
                LEFT JOIN (
                  SELECT target_id, target_type, COUNT(*) AS like_count
                  FROM like_record
                  WHERE status = 1
                  GROUP BY target_id, target_type
                ) l ON l.target_id = s.target_id AND l.target_type = s.target_type
                LEFT JOIN (
                  SELECT target_id, target_type, COUNT(*) AS favorite_count
                  FROM favorite_record
                  WHERE status = 1
                  GROUP BY target_id, target_type
                ) f ON f.target_id = s.target_id AND f.target_type = s.target_type
                LEFT JOIN (
                  SELECT content_id AS target_id, content_type AS target_type, COUNT(*) AS comment_count
                  FROM comment
                  WHERE deleted = 0
                  GROUP BY content_id, content_type
                ) c ON c.target_id = s.target_id AND c.target_type = s.target_type
                LEFT JOIN article a ON s.target_type = 'ARTICLE' AND a.id = s.target_id
                LEFT JOIN video v ON s.target_type = 'VIDEO' AND v.id = s.target_id
                ORDER BY score DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Candidate candidate = new Candidate();
            candidate.targetId = rs.getLong("target_id");
            candidate.targetType = rs.getString("target_type");
            candidate.title = rs.getString("title");
            candidate.status = rs.getString("status");
            candidate.viewCount = rs.getLong("view_count");
            candidate.playCount = rs.getLong("play_count");
            candidate.likeCount = rs.getLong("like_count");
            candidate.favoriteCount = rs.getLong("favorite_count");
            candidate.commentCount = rs.getLong("comment_count");
            candidate.score = rs.getDouble("score");
            candidate.reason = "近期热门内容";
            candidate.recallSource = "HOT";
            return candidate;
        }, params.toArray());
    }

    private void mergeCandidate(Map<String, Candidate> map, Candidate incoming) {
        String key = incoming.targetType + ":" + incoming.targetId;
        Candidate existed = map.get(key);
        if (existed == null) {
            map.put(key, incoming);
            return;
        }

        existed.score += incoming.score * 0.65;
        existed.reason = existed.reason + "；" + incoming.reason;
        existed.recallSource = existed.recallSource + "," + incoming.recallSource;
        existed.viewCount = Math.max(existed.viewCount, incoming.viewCount);
        existed.playCount = Math.max(existed.playCount, incoming.playCount);
        existed.likeCount = Math.max(existed.likeCount, incoming.likeCount);
        existed.favoriteCount = Math.max(existed.favoriteCount, incoming.favoriteCount);
        existed.commentCount = Math.max(existed.commentCount, incoming.commentCount);
    }

    private void saveRecallLogs(Long userId, String requestId, List<RecommendationItem> items) {
        for (RecommendationItem item : items) {
            jdbcTemplate.update("""
                    INSERT INTO recommend_recall_log(user_id, target_id, target_type, recall_source, recall_score, final_score, reason, request_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    userId,
                    item.targetId(),
                    item.targetType(),
                    item.recallSource(),
                    item.score(),
                    item.score(),
                    item.reason(),
                    requestId
            );
        }
    }

    private Map<String, Double> loadTypeBoost(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT target_type, action_type, COUNT(*) AS cnt
                FROM user_behavior_log
                WHERE user_id = ?
                  AND created_at >= DATE_SUB(NOW(), INTERVAL 60 DAY)
                GROUP BY target_type, action_type
                """, userId);

        Map<String, Double> score = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String targetType = String.valueOf(row.get("target_type"));
            String actionType = String.valueOf(row.get("action_type"));
            long count = ((Number) row.get("cnt")).longValue();

            double weight = switch (actionType) {
                case "LIKE" -> 0.08;
                case "FAVORITE" -> 0.12;
                case "COMMENT" -> 0.1;
                case "PLAY" -> 0.06;
                case "VIEW" -> 0.03;
                default -> 0.01;
            };

            score.merge(targetType, count * weight, Double::sum);
        }

        Map<String, Double> boost = new HashMap<>();
        boost.put("ARTICLE", 1.0 + Math.min(score.getOrDefault("ARTICLE", 0.0), 0.8));
        boost.put("VIDEO", 1.0 + Math.min(score.getOrDefault("VIDEO", 0.0), 0.8));
        return boost;
    }

    private List<Long> recentlyExposedIds(Long userId, String scene) {
        return jdbcTemplate.query("""
                SELECT target_id
                FROM recommend_exposure_log
                WHERE user_id = ?
                  AND scene = ?
                  AND created_at >= DATE_SUB(NOW(), INTERVAL 10 MINUTE)
                ORDER BY id DESC
                LIMIT 100
                """, (rs, rowNum) -> rs.getLong("target_id"), userId, scene);
    }

    private RecommendationItem copyWithScore(RecommendationItem item, double score, String reason, String source) {
        return new RecommendationItem(
                item.targetId(),
                item.targetType(),
                item.title(),
                item.status(),
                Math.round(score * 100.0) / 100.0,
                item.viewCount(),
                item.playCount(),
                item.likeCount(),
                item.favoriteCount(),
                item.commentCount(),
                reason,
                source
        );
    }

    private int normalizeLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }

    private static class Candidate {
        Long targetId;
        String targetType;
        String title;
        String status;
        double score;
        long viewCount;
        long playCount;
        long likeCount;
        long favoriteCount;
        long commentCount;
        String reason;
        String recallSource;

        double score() {
            return score;
        }

        RecommendationItem toItem() {
            return new RecommendationItem(
                    targetId,
                    targetType,
                    title,
                    status,
                    Math.round(score * 100.0) / 100.0,
                    viewCount,
                    playCount,
                    likeCount,
                    favoriteCount,
                    commentCount,
                    reason,
                    recallSource
            );
        }
    }
}
