package com.vspicy.dashboard.service;

import com.vspicy.dashboard.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class DashboardService {
    private final JdbcTemplate jdbcTemplate;

    public DashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardOverviewResponse overview() {
        List<MetricCard> metrics = new ArrayList<>();

        metrics.add(new MetricCard("users", "用户总数", countTable("sys_user"), "人", "平台注册用户规模"));
        metrics.add(new MetricCard("videos", "视频总数", countTable("video"), "个", "已创建视频内容"));
        metrics.add(new MetricCard("publishedVideos", "已发布视频", countWhere("video", "status = 'PUBLISHED'"), "个", "可播放视频内容"));
        metrics.add(new MetricCard("articles", "文章总数", countTable("article"), "篇", "文章内容总量"));
        metrics.add(new MetricCard("publishedArticles", "已发布文章", countWhere("article", "status = 'PUBLISHED'"), "篇", "已审核通过文章"));
        metrics.add(new MetricCard("comments", "评论总数", countWhere("comment", "deleted = 0"), "条", "用户评论规模"));
        metrics.add(new MetricCard("likes", "点赞总数", countWhere("like_record", "status = 1"), "次", "内容点赞规模"));
        metrics.add(new MetricCard("favorites", "收藏总数", countWhere("favorite_record", "status = 1"), "次", "内容收藏规模"));
        metrics.add(new MetricCard("behaviors", "行为日志", countTable("user_behavior_log"), "条", "浏览/播放/互动行为"));
        metrics.add(new MetricCard("exposures", "推荐曝光", countTable("recommend_exposure_log"), "次", "推荐系统曝光量"));
        metrics.add(new MetricCard("pendingAudits", "待审核", countWhere("audit_task", "status = 'PENDING'"), "条", "需要人工处理的审核任务"));
        metrics.add(new MetricCard("failedTranscodes", "转码失败", countWhere("video_transcode_task", "status = 'FAILED'"), "个", "需要补偿的转码任务"));

        return new DashboardOverviewResponse(metrics);
    }

    public List<TrendPoint> trends(Integer days) {
        int safeDays = days == null || days <= 0 || days > 30 ? 7 : days;
        Map<String, MutableTrend> map = new LinkedHashMap<>();

        LocalDate start = LocalDate.now().minusDays(safeDays - 1);
        for (int i = 0; i < safeDays; i++) {
            String date = start.plusDays(i).toString();
            map.put(date, new MutableTrend(date));
        }

        if (tableExists("user_behavior_log")) {
            List<Map<String, Object>> behaviorRows = jdbcTemplate.queryForList("""
                    SELECT DATE(created_at) AS stat_date,
                           SUM(CASE WHEN action_type = 'VIEW' THEN 1 ELSE 0 END) AS view_count,
                           SUM(CASE WHEN action_type = 'PLAY' THEN 1 ELSE 0 END) AS play_count,
                           SUM(CASE WHEN action_type = 'LIKE' THEN 1 ELSE 0 END) AS like_count,
                           SUM(CASE WHEN action_type = 'FAVORITE' THEN 1 ELSE 0 END) AS favorite_count,
                           SUM(CASE WHEN action_type = 'COMMENT' THEN 1 ELSE 0 END) AS comment_count
                    FROM user_behavior_log
                    WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                    GROUP BY DATE(created_at)
                    ORDER BY stat_date
                    """, safeDays - 1);

            for (Map<String, Object> row : behaviorRows) {
                String date = String.valueOf(row.get("stat_date"));
                MutableTrend trend = map.computeIfAbsent(date, MutableTrend::new);
                trend.viewCount = number(row.get("view_count"));
                trend.playCount = number(row.get("play_count"));
                trend.likeCount = number(row.get("like_count"));
                trend.favoriteCount = number(row.get("favorite_count"));
                trend.commentCount = number(row.get("comment_count"));
            }
        }

        if (tableExists("recommend_exposure_log")) {
            List<Map<String, Object>> exposureRows = jdbcTemplate.queryForList("""
                    SELECT DATE(created_at) AS stat_date, COUNT(*) AS exposure_count
                    FROM recommend_exposure_log
                    WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                    GROUP BY DATE(created_at)
                    ORDER BY stat_date
                    """, safeDays - 1);

            for (Map<String, Object> row : exposureRows) {
                String date = String.valueOf(row.get("stat_date"));
                MutableTrend trend = map.computeIfAbsent(date, MutableTrend::new);
                trend.exposureCount = number(row.get("exposure_count"));
            }
        }

        return map.values().stream().map(MutableTrend::toPoint).toList();
    }

    public List<ContentRankItem> contentRank(Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 100 ? 20 : limit;
        if (!tableExists("user_behavior_log")) {
            return List.of();
        }

        return jdbcTemplate.query("""
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
                  ) AS hot_score
                FROM (
                  SELECT
                    target_id,
                    target_type,
                    SUM(CASE WHEN action_type = 'VIEW' THEN 1 ELSE 0 END) AS view_count,
                    SUM(CASE WHEN action_type = 'PLAY' THEN 1 ELSE 0 END) AS play_count
                  FROM user_behavior_log
                  WHERE created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                  GROUP BY target_id, target_type
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
                ORDER BY hot_score DESC
                LIMIT ?
                """, (rs, rowNum) -> new ContentRankItem(
                rs.getLong("target_id"),
                rs.getString("target_type"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getLong("view_count"),
                rs.getLong("play_count"),
                rs.getLong("like_count"),
                rs.getLong("favorite_count"),
                rs.getLong("comment_count"),
                rs.getDouble("hot_score")
        ), safeLimit);
    }

    private long countTable(String table) {
        if (!tableExists(table)) {
            return 0;
        }
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private long countWhere(String table, String whereClause) {
        if (!tableExists(table)) {
            return 0;
        }
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + whereClause, Long.class);
    }

    private boolean tableExists(String table) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, Long.class, table);
        return count != null && count > 0;
    }

    private long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static class MutableTrend {
        String date;
        long viewCount;
        long playCount;
        long likeCount;
        long favoriteCount;
        long commentCount;
        long exposureCount;

        MutableTrend(String date) {
            this.date = date;
        }

        TrendPoint toPoint() {
            return new TrendPoint(date, viewCount, playCount, likeCount, favoriteCount, commentCount, exposureCount);
        }
    }
}
