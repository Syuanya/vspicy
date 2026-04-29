package com.vspicy.video.service;

import com.vspicy.video.dto.UserStorageAlertView;
import com.vspicy.video.dto.UserStorageRankView;
import com.vspicy.video.dto.VideoStorageDashboardView;
import com.vspicy.video.dto.VideoStorageScanResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VideoStorageDashboardService {
    private final JdbcTemplate jdbcTemplate;
    private final VideoStorageScanService storageScanService;

    public VideoStorageDashboardService(
            JdbcTemplate jdbcTemplate,
            VideoStorageScanService storageScanService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageScanService = storageScanService;
    }

    public VideoStorageDashboardView dashboard(String prefix, Integer limit, Integer threshold) {
        int safeThreshold = normalizeThreshold(threshold);
        VideoStorageScanResult scan = storageScanService.scan(prefix, limit);

        Long minioTotalBytes = scan.items().stream()
                .filter(item -> "OBJECT_MISSING_DB".equals(item.issueType()))
                .map(item -> item.size() == null ? 0L : item.size())
                .reduce(0L, Long::sum);

        // Phase40 的 scan result 只返回异常 items。这里的 minioTotalBytes 是孤儿对象大小合计。
        // 为避免误导，字段名仍保留为 minioTotalBytes，但页面显示为“孤儿对象大小”。
        Long totalUsedMb = queryLong("""
                SELECT COALESCE(SUM(used_mb), 0)
                FROM video_upload_quota_usage
                WHERE quota_scope = 'TOTAL'
                  AND scope_key = 'TOTAL'
                """);

        Long userCount = queryLong("""
                SELECT COUNT(DISTINCT user_id)
                FROM video_upload_quota_usage
                WHERE quota_scope = 'TOTAL'
                  AND scope_key = 'TOTAL'
                """);

        Long confirmedRecordCount = queryLong("""
                SELECT COUNT(*)
                FROM video_upload_record
                WHERE status = 'CONFIRMED'
                """);

        Long releasedRecordCount = queryLong("""
                SELECT COUNT(*)
                FROM video_upload_record
                WHERE status = 'RELEASED'
                """);

        List<UserStorageRankView> topUsers = topUsers(20);
        List<UserStorageAlertView> alerts = alerts(safeThreshold, 50);

        return new VideoStorageDashboardView(
                scan.bucket(),
                scan.prefix(),
                scan.limit(),
                safeThreshold,
                scan.minioObjectCount(),
                minioTotalBytes,
                bytesToMb(minioTotalBytes),
                scan.dbObjectCount(),
                scan.dbMissingObjectCount(),
                scan.objectMissingDbCount(),
                userCount,
                totalUsedMb,
                confirmedRecordCount,
                releasedRecordCount,
                topUsers,
                alerts
        );
    }

    private List<UserStorageRankView> topUsers(int limit) {
        return jdbcTemplate.query("""
                SELECT
                  base.user_id,
                  su.username,
                  COALESCE(um.plan_code, 'FREE') AS plan_code,
                  COALESCE(total.used_mb, 0) AS total_used_mb,
                  COALESCE(policy.total_limit_mb, free_policy.total_limit_mb, 5120) AS total_limit_mb,
                  COALESCE(record_stat.confirmed_count, 0) AS confirmed_record_count,
                  COALESCE(record_stat.released_count, 0) AS released_record_count
                FROM (
                  SELECT user_id FROM video_upload_quota_usage WHERE quota_scope = 'TOTAL'
                  UNION
                  SELECT user_id FROM video_upload_record
                  UNION
                  SELECT id AS user_id FROM sys_user
                ) base
                LEFT JOIN sys_user su ON su.id = base.user_id
                LEFT JOIN user_membership um
                  ON um.user_id = base.user_id
                 AND um.status = 'ACTIVE'
                 AND NOW() BETWEEN um.start_at AND um.end_at
                LEFT JOIN video_upload_quota_policy policy
                  ON policy.plan_code = COALESCE(um.plan_code, 'FREE')
                 AND policy.status = 1
                LEFT JOIN video_upload_quota_policy free_policy
                  ON free_policy.plan_code = 'FREE'
                 AND free_policy.status = 1
                LEFT JOIN video_upload_quota_usage total
                  ON total.user_id = base.user_id
                 AND total.quota_scope = 'TOTAL'
                 AND total.scope_key = 'TOTAL'
                LEFT JOIN (
                  SELECT
                    user_id,
                    SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) AS confirmed_count,
                    SUM(CASE WHEN status = 'RELEASED' THEN 1 ELSE 0 END) AS released_count
                  FROM video_upload_record
                  GROUP BY user_id
                ) record_stat ON record_stat.user_id = base.user_id
                ORDER BY total_used_mb DESC, base.user_id ASC
                LIMIT ?
                """, (rs, rowNum) -> {
            long totalUsedMb = rs.getLong("total_used_mb");
            long totalLimitMb = rs.getLong("total_limit_mb");
            return new UserStorageRankView(
                    rs.getLong("user_id"),
                    rs.getString("username"),
                    rs.getString("plan_code"),
                    totalUsedMb,
                    totalLimitMb,
                    usagePercent(totalUsedMb, totalLimitMb),
                    rs.getLong("confirmed_record_count"),
                    rs.getLong("released_record_count")
            );
        }, limit);
    }

    private List<UserStorageAlertView> alerts(Integer threshold, int limit) {
        return jdbcTemplate.query("""
                SELECT *
                FROM (
                  SELECT
                    base.user_id,
                    su.username,
                    COALESCE(um.plan_code, 'FREE') AS plan_code,
                    COALESCE(total.used_mb, 0) AS total_used_mb,
                    COALESCE(policy.total_limit_mb, free_policy.total_limit_mb, 5120) AS total_limit_mb
                  FROM (
                    SELECT user_id FROM video_upload_quota_usage WHERE quota_scope = 'TOTAL'
                    UNION
                    SELECT user_id FROM video_upload_record
                    UNION
                    SELECT id AS user_id FROM sys_user
                  ) base
                  LEFT JOIN sys_user su ON su.id = base.user_id
                  LEFT JOIN user_membership um
                    ON um.user_id = base.user_id
                   AND um.status = 'ACTIVE'
                   AND NOW() BETWEEN um.start_at AND um.end_at
                  LEFT JOIN video_upload_quota_policy policy
                    ON policy.plan_code = COALESCE(um.plan_code, 'FREE')
                   AND policy.status = 1
                  LEFT JOIN video_upload_quota_policy free_policy
                    ON free_policy.plan_code = 'FREE'
                   AND free_policy.status = 1
                  LEFT JOIN video_upload_quota_usage total
                    ON total.user_id = base.user_id
                   AND total.quota_scope = 'TOTAL'
                   AND total.scope_key = 'TOTAL'
                ) t
                WHERE total_limit_mb > 0
                  AND FLOOR(total_used_mb * 100 / total_limit_mb) >= ?
                ORDER BY FLOOR(total_used_mb * 100 / total_limit_mb) DESC, total_used_mb DESC
                LIMIT ?
                """, (rs, rowNum) -> {
            long totalUsedMb = rs.getLong("total_used_mb");
            long totalLimitMb = rs.getLong("total_limit_mb");
            int percent = usagePercent(totalUsedMb, totalLimitMb);
            return new UserStorageAlertView(
                    rs.getLong("user_id"),
                    rs.getString("username"),
                    rs.getString("plan_code"),
                    totalUsedMb,
                    totalLimitMb,
                    percent,
                    alertLevel(percent),
                    "用户空间使用率已达到 " + percent + "%"
            );
        }, threshold, limit);
    }

    private String alertLevel(int percent) {
        if (percent >= 95) {
            return "CRITICAL";
        }
        if (percent >= 90) {
            return "HIGH";
        }
        if (percent >= 80) {
            return "WARN";
        }
        return "INFO";
    }

    private int usagePercent(long used, long limit) {
        if (limit <= 0) {
            return 0;
        }
        return (int) Math.min(100, Math.floor((used * 100.0) / limit));
    }

    private int normalizeThreshold(Integer threshold) {
        if (threshold == null || threshold <= 0 || threshold > 100) {
            return 80;
        }
        return threshold;
    }

    private Long queryLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private long bytesToMb(long bytes) {
        if (bytes <= 0) {
            return 0;
        }
        return Math.max(1L, (bytes + 1024L * 1024L - 1L) / (1024L * 1024L));
    }
}
