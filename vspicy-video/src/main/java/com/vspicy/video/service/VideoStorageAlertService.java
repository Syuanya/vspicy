package com.vspicy.video.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VideoStorageAlertService {
    private final JdbcTemplate jdbcTemplate;
    private final VideoStorageDashboardService dashboardService;
    private final VideoStorageScanService storageScanService;
    private final HlsIntegrityService hlsIntegrityService;

    public VideoStorageAlertService(
            JdbcTemplate jdbcTemplate,
            VideoStorageDashboardService dashboardService,
            VideoStorageScanService storageScanService,
            HlsIntegrityService hlsIntegrityService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dashboardService = dashboardService;
        this.storageScanService = storageScanService;
        this.hlsIntegrityService = hlsIntegrityService;
    }

    public List<VideoStorageAlertView> list(String status, String level, Integer limit) {
        String safeStatus = status == null || status.isBlank() ? "OPEN" : status.trim();
        String safeLevel = level == null ? "" : level.trim();
        int safeLimit = normalizeLimit(limit);

        if (safeLevel.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT id, dedup_key, alert_code, alert_level, title, content,
                           target_type, target_id, object_key, user_id, video_id,
                           source, status, first_seen_at, last_seen_at,
                           acked_at, resolved_at, created_at, updated_at
                    FROM video_storage_alert_event
                    WHERE status = ?
                    ORDER BY
                      CASE alert_level
                        WHEN 'CRITICAL' THEN 1
                        WHEN 'HIGH' THEN 2
                        WHEN 'WARN' THEN 3
                        ELSE 4
                      END,
                      last_seen_at DESC
                    LIMIT ?
                    """, (rs, rowNum) -> mapAlert(rs), safeStatus, safeLimit);
        }

        return jdbcTemplate.query("""
                SELECT id, dedup_key, alert_code, alert_level, title, content,
                       target_type, target_id, object_key, user_id, video_id,
                       source, status, first_seen_at, last_seen_at,
                       acked_at, resolved_at, created_at, updated_at
                FROM video_storage_alert_event
                WHERE status = ?
                  AND alert_level = ?
                ORDER BY last_seen_at DESC
                LIMIT ?
                """, (rs, rowNum) -> mapAlert(rs), safeStatus, safeLevel, safeLimit);
    }

    @Transactional
    public VideoStorageAlertGenerateResult generate(VideoStorageAlertGenerateCommand command) {
        String prefix = command == null || command.prefix() == null || command.prefix().isBlank()
                ? "videos/"
                : command.prefix();
        int limit = command == null || command.limit() == null ? 1000 : command.limit();
        int threshold = command == null || command.threshold() == null ? 80 : command.threshold();
        int hlsLimit = command == null || command.hlsLimit() == null ? 200 : command.hlsLimit();

        long generated = 0L;

        VideoStorageDashboardView dashboard = dashboardService.dashboard(prefix, limit, threshold);
        for (UserStorageAlertView alert : dashboard.alerts()) {
            upsertAlert(
                    "STORAGE_USAGE_WARN:user:" + alert.userId(),
                    "STORAGE_USAGE_WARN",
                    alert.level(),
                    "用户空间容量告警",
                    alert.message() + "，used=" + alert.totalUsedMb() + "MB，limit=" + alert.totalLimitMb() + "MB",
                    "USER",
                    String.valueOf(alert.userId()),
                    null,
                    alert.userId(),
                    null,
                    "STORAGE_DASHBOARD"
            );
            generated++;
        }

        VideoStorageScanResult scan = storageScanService.scan(prefix, limit);
        for (VideoStorageScanItem item : scan.items()) {
            if ("OBJECT_MISSING_DB".equals(item.issueType())) {
                upsertAlert(
                        "OBJECT_MISSING_DB:" + item.objectKey(),
                        "OBJECT_MISSING_DB",
                        "WARN",
                        "发现孤儿对象",
                        item.message(),
                        "OBJECT",
                        item.objectKey(),
                        item.objectKey(),
                        null,
                        item.videoId(),
                        "STORAGE_SCAN"
                );
                generated++;
            } else if ("DB_MISSING_OBJECT".equals(item.issueType())) {
                upsertAlert(
                        "DB_MISSING_OBJECT:" + item.objectKey(),
                        "DB_MISSING_OBJECT",
                        "HIGH",
                        "数据库记录对应对象缺失",
                        item.message(),
                        "OBJECT",
                        item.objectKey(),
                        item.objectKey(),
                        null,
                        item.videoId(),
                        "STORAGE_SCAN"
                );
                generated++;
            }
        }

        HlsIntegrityResult hls = hlsIntegrityService.scan(prefix, hlsLimit);
        for (HlsIntegrityItem item : hls.items()) {
            if ("HLS_OK".equals(item.status())) {
                continue;
            }

            String level = switch (item.status()) {
                case "HLS_MANIFEST_MISSING", "HLS_SEGMENT_MISSING" -> "CRITICAL";
                case "HLS_MANIFEST_READ_FAILED" -> "HIGH";
                default -> "WARN";
            };

            upsertAlert(
                    item.status() + ":" + item.manifestObjectKey(),
                    item.status(),
                    level,
                    "HLS 产物异常",
                    item.message()
                            + "，manifest="
                            + item.manifestObjectKey()
                            + "，missingSegments="
                            + item.missingSegmentCount(),
                    "HLS",
                    item.manifestObjectKey(),
                    item.manifestObjectKey(),
                    null,
                    item.videoId(),
                    "HLS_INTEGRITY"
            );
            generated++;
        }

        return new VideoStorageAlertGenerateResult(
                prefix,
                limit,
                threshold,
                hlsLimit,
                generated,
                countByStatus("OPEN"),
                countByStatus("ACKED"),
                countByStatus("RESOLVED"),
                "存储告警生成完成"
        );
    }

    @Transactional
    public VideoStorageAlertView ack(Long id) {
        if (id == null) {
            throw new BizException("id 不能为空");
        }

        int updated = jdbcTemplate.update("""
                UPDATE video_storage_alert_event
                SET status = 'ACKED',
                    acked_at = NOW()
                WHERE id = ?
                  AND status = 'OPEN'
                """, id);

        if (updated <= 0) {
            throw new BizException("告警不存在或当前状态不可确认");
        }

        return findById(id);
    }

    @Transactional
    public VideoStorageAlertView resolve(Long id) {
        if (id == null) {
            throw new BizException("id 不能为空");
        }

        int updated = jdbcTemplate.update("""
                UPDATE video_storage_alert_event
                SET status = 'RESOLVED',
                    resolved_at = NOW()
                WHERE id = ?
                  AND status IN ('OPEN', 'ACKED')
                """, id);

        if (updated <= 0) {
            throw new BizException("告警不存在或当前状态不可解决");
        }

        return findById(id);
    }

    private void upsertAlert(
            String dedupKey,
            String alertCode,
            String level,
            String title,
            String content,
            String targetType,
            String targetId,
            String objectKey,
            Long userId,
            Long videoId,
            String source
    ) {
        jdbcTemplate.update("""
                INSERT INTO video_storage_alert_event(
                  dedup_key, alert_code, alert_level, title, content,
                  target_type, target_id, object_key, user_id, video_id, source,
                  status, first_seen_at, last_seen_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', NOW(), NOW())
                ON DUPLICATE KEY UPDATE
                  alert_level = VALUES(alert_level),
                  title = VALUES(title),
                  content = VALUES(content),
                  target_type = VALUES(target_type),
                  target_id = VALUES(target_id),
                  object_key = VALUES(object_key),
                  user_id = VALUES(user_id),
                  video_id = VALUES(video_id),
                  source = VALUES(source),
                  last_seen_at = NOW(),
                  status = CASE
                    WHEN status = 'RESOLVED' THEN 'OPEN'
                    ELSE status
                  END
                """, dedupKey, alertCode, level, title, content,
                targetType, targetId, objectKey, userId, videoId, source);
    }

    private VideoStorageAlertView findById(Long id) {
        List<VideoStorageAlertView> rows = jdbcTemplate.query("""
                SELECT id, dedup_key, alert_code, alert_level, title, content,
                       target_type, target_id, object_key, user_id, video_id,
                       source, status, first_seen_at, last_seen_at,
                       acked_at, resolved_at, created_at, updated_at
                FROM video_storage_alert_event
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapAlert(rs), id);

        if (rows.isEmpty()) {
            throw new BizException("告警不存在");
        }

        return rows.get(0);
    }

    private long countByStatus(String status) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM video_storage_alert_event
                WHERE status = ?
                """, Long.class, status);
        return value == null ? 0L : value;
    }

    private VideoStorageAlertView mapAlert(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new VideoStorageAlertView(
                rs.getLong("id"),
                rs.getString("dedup_key"),
                rs.getString("alert_code"),
                rs.getString("alert_level"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("object_key"),
                nullableLong(rs.getObject("user_id")),
                nullableLong(rs.getObject("video_id")),
                rs.getString("source"),
                rs.getString("status"),
                rs.getString("first_seen_at"),
                rs.getString("last_seen_at"),
                rs.getString("acked_at"),
                rs.getString("resolved_at"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 || limit > 500 ? 100 : limit;
    }
}
