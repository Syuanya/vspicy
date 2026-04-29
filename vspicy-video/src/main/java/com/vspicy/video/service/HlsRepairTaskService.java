package com.vspicy.video.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.StringJoiner;

@Service
public class HlsRepairTaskService {
    private final JdbcTemplate jdbcTemplate;
    private final HlsIntegrityService hlsIntegrityService;

    public HlsRepairTaskService(
            JdbcTemplate jdbcTemplate,
            HlsIntegrityService hlsIntegrityService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.hlsIntegrityService = hlsIntegrityService;
    }

    public List<HlsRepairTaskView> list(String status, Integer limit) {
        String safeStatus = status == null ? "" : status.trim();
        int safeLimit = normalizeLimit(limit);

        if (safeStatus.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT id, dedup_key, repair_type, status, priority, bucket,
                           manifest_object_key, missing_segments, missing_segment_count,
                           video_id, record_id, trace_id, alert_id, source,
                           retry_count, max_retry_count, last_error,
                           dispatched_at, started_at, finished_at, created_at, updated_at
                    FROM video_hls_repair_task
                    ORDER BY
                      CASE status
                        WHEN 'PENDING' THEN 1
                        WHEN 'FAILED' THEN 2
                        WHEN 'DISPATCHED' THEN 3
                        WHEN 'RUNNING' THEN 4
                        ELSE 5
                      END,
                      priority ASC,
                      id DESC
                    LIMIT ?
                    """, (rs, rowNum) -> mapTask(rs), safeLimit);
        }

        return jdbcTemplate.query("""
                SELECT id, dedup_key, repair_type, status, priority, bucket,
                       manifest_object_key, missing_segments, missing_segment_count,
                       video_id, record_id, trace_id, alert_id, source,
                       retry_count, max_retry_count, last_error,
                       dispatched_at, started_at, finished_at, created_at, updated_at
                FROM video_hls_repair_task
                WHERE status = ?
                ORDER BY priority ASC, id DESC
                LIMIT ?
                """, (rs, rowNum) -> mapTask(rs), safeStatus, safeLimit);
    }

    @Transactional
    public HlsRepairGenerateResult generate(HlsRepairGenerateCommand command) {
        String prefix = command == null || command.prefix() == null || command.prefix().isBlank()
                ? "videos/"
                : command.prefix();
        int limit = command == null || command.limit() == null ? 200 : normalizeLimit(command.limit());

        HlsIntegrityResult integrity = hlsIntegrityService.scan(prefix, limit);
        long created = 0L;
        long existing = 0L;

        for (HlsIntegrityItem item : integrity.items()) {
            if ("HLS_OK".equals(item.status())) {
                continue;
            }

            if (!isRepairable(item.status())) {
                continue;
            }

            int affected = createTaskFromIntegrity(item, null);
            if (affected > 0) {
                created++;
            } else {
                existing++;
            }
        }

        return new HlsRepairGenerateResult(
                integrity.manifestCount(),
                created,
                existing,
                countByStatus("PENDING"),
                countByStatus("FAILED"),
                "HLS 修复任务生成完成"
        );
    }

    @Transactional
    public HlsRepairGenerateResult generateFromAlerts(HlsRepairGenerateFromAlertCommand command) {
        int limit = command == null || command.limit() == null ? 100 : normalizeLimit(command.limit());

        List<AlertSnapshot> alerts = jdbcTemplate.query("""
                SELECT id, alert_code, alert_level, object_key, video_id, content
                FROM video_storage_alert_event
                WHERE status = 'OPEN'
                  AND alert_code IN (
                    'HLS_MANIFEST_MISSING',
                    'HLS_SEGMENT_MISSING',
                    'HLS_MANIFEST_EMPTY',
                    'HLS_MANIFEST_READ_FAILED'
                  )
                ORDER BY
                  CASE alert_level
                    WHEN 'CRITICAL' THEN 1
                    WHEN 'HIGH' THEN 2
                    WHEN 'WARN' THEN 3
                    ELSE 4
                  END,
                  id DESC
                LIMIT ?
                """, (rs, rowNum) -> new AlertSnapshot(
                rs.getLong("id"),
                rs.getString("alert_code"),
                rs.getString("alert_level"),
                rs.getString("object_key"),
                nullableLong(rs.getObject("video_id")),
                rs.getString("content")
        ), limit);

        long created = 0L;
        long existing = 0L;

        for (AlertSnapshot alert : alerts) {
            String manifestObjectKey = alert.objectKey();
            if (manifestObjectKey == null || manifestObjectKey.isBlank()) {
                continue;
            }

            String repairType = toRepairType(alert.alertCode());
            String dedupKey = repairType + ":" + manifestObjectKey;

            int affected = jdbcTemplate.update("""
                    INSERT IGNORE INTO video_hls_repair_task(
                      dedup_key, repair_type, status, priority, bucket,
                      manifest_object_key, missing_segments, missing_segment_count,
                      video_id, alert_id, source, last_error
                    )
                    VALUES (?, ?, 'PENDING', ?, 'vspicy', ?, NULL, 0, ?, ?, 'STORAGE_ALERT', ?)
                    """, dedupKey, repairType, priority(repairType), manifestObjectKey,
                    alert.videoId(), alert.id(), alert.content());

            if (affected > 0) {
                created++;
            } else {
                existing++;
            }
        }

        return new HlsRepairGenerateResult(
                (long) alerts.size(),
                created,
                existing,
                countByStatus("PENDING"),
                countByStatus("FAILED"),
                "从存储告警生成 HLS 修复任务完成"
        );
    }

    @Transactional
    public HlsRepairTaskView retry(Long id) {
        HlsRepairTaskView existing = findById(id);
        if (!"FAILED".equals(existing.status()) && !"CANCELED".equals(existing.status())) {
            throw new BizException("只有 FAILED/CANCELED 任务可以重试");
        }

        jdbcTemplate.update("""
                UPDATE video_hls_repair_task
                SET status = 'PENDING',
                    retry_count = retry_count + 1,
                    last_error = NULL,
                    dispatched_at = NULL,
                    started_at = NULL,
                    finished_at = NULL
                WHERE id = ?
                """, id);

        return findById(id);
    }

    @Transactional
    public HlsRepairTaskView cancel(Long id) {
        HlsRepairTaskView existing = findById(id);
        if ("SUCCESS".equals(existing.status())) {
            throw new BizException("SUCCESS 任务不能取消");
        }

        jdbcTemplate.update("""
                UPDATE video_hls_repair_task
                SET status = 'CANCELED',
                    finished_at = NOW()
                WHERE id = ?
                """, id);

        return findById(id);
    }

    @Transactional
    public HlsRepairTaskView markSuccess(Long id) {
        findById(id);

        jdbcTemplate.update("""
                UPDATE video_hls_repair_task
                SET status = 'SUCCESS',
                    last_error = NULL,
                    finished_at = NOW()
                WHERE id = ?
                """, id);

        return findById(id);
    }

    @Transactional
    public HlsRepairTaskView markFailed(Long id, HlsRepairFailCommand command) {
        findById(id);
        String errorMessage = command == null || command.errorMessage() == null || command.errorMessage().isBlank()
                ? "手动标记失败"
                : command.errorMessage();

        jdbcTemplate.update("""
                UPDATE video_hls_repair_task
                SET status = 'FAILED',
                    last_error = ?,
                    finished_at = NOW()
                WHERE id = ?
                """, errorMessage, id);

        return findById(id);
    }

    private int createTaskFromIntegrity(HlsIntegrityItem item, Long alertId) {
        String repairType = toRepairType(item.status());
        String manifest = item.manifestObjectKey();
        String dedupKey = repairType + ":" + manifest;
        String missingSegments = joinSegments(item.missingSegments());

        return jdbcTemplate.update("""
                INSERT IGNORE INTO video_hls_repair_task(
                  dedup_key, repair_type, status, priority, bucket,
                  manifest_object_key, missing_segments, missing_segment_count,
                  video_id, record_id, trace_id, alert_id, source, last_error
                )
                VALUES (?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?, 'HLS_INTEGRITY', ?)
                """, dedupKey, repairType, priority(repairType), item.bucket(),
                manifest, missingSegments, item.missingSegmentCount(),
                item.videoId(), item.recordId(), item.traceId(), alertId, item.message());
    }

    private boolean isRepairable(String status) {
        return "HLS_MANIFEST_MISSING".equals(status)
                || "HLS_SEGMENT_MISSING".equals(status)
                || "HLS_MANIFEST_EMPTY".equals(status)
                || "HLS_MANIFEST_READ_FAILED".equals(status);
    }

    private String toRepairType(String statusOrCode) {
        return switch (statusOrCode) {
            case "HLS_MANIFEST_MISSING" -> "MANIFEST_MISSING";
            case "HLS_SEGMENT_MISSING" -> "SEGMENT_MISSING";
            case "HLS_MANIFEST_EMPTY" -> "MANIFEST_EMPTY";
            case "HLS_MANIFEST_READ_FAILED" -> "MANIFEST_READ_FAILED";
            default -> statusOrCode;
        };
    }

    private int priority(String repairType) {
        return switch (repairType) {
            case "MANIFEST_MISSING" -> 10;
            case "SEGMENT_MISSING" -> 20;
            case "MANIFEST_READ_FAILED" -> 30;
            case "MANIFEST_EMPTY" -> 40;
            default -> 100;
        };
    }

    private String joinSegments(List<String> segments) {
        if (segments == null || segments.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (String segment : segments) {
            joiner.add(segment);
        }
        return joiner.toString();
    }

    private HlsRepairTaskView findById(Long id) {
        if (id == null) {
            throw new BizException("id 不能为空");
        }

        List<HlsRepairTaskView> rows = jdbcTemplate.query("""
                SELECT id, dedup_key, repair_type, status, priority, bucket,
                       manifest_object_key, missing_segments, missing_segment_count,
                       video_id, record_id, trace_id, alert_id, source,
                       retry_count, max_retry_count, last_error,
                       dispatched_at, started_at, finished_at, created_at, updated_at
                FROM video_hls_repair_task
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapTask(rs), id);

        if (rows.isEmpty()) {
            throw new BizException("HLS 修复任务不存在");
        }

        return rows.get(0);
    }

    private long countByStatus(String status) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM video_hls_repair_task
                WHERE status = ?
                """, Long.class, status);
        return value == null ? 0L : value;
    }

    private HlsRepairTaskView mapTask(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new HlsRepairTaskView(
                rs.getLong("id"),
                rs.getString("dedup_key"),
                rs.getString("repair_type"),
                rs.getString("status"),
                rs.getInt("priority"),
                rs.getString("bucket"),
                rs.getString("manifest_object_key"),
                rs.getString("missing_segments"),
                rs.getInt("missing_segment_count"),
                nullableLong(rs.getObject("video_id")),
                nullableLong(rs.getObject("record_id")),
                nullableLong(rs.getObject("trace_id")),
                nullableLong(rs.getObject("alert_id")),
                rs.getString("source"),
                rs.getInt("retry_count"),
                rs.getInt("max_retry_count"),
                rs.getString("last_error"),
                rs.getString("dispatched_at"),
                rs.getString("started_at"),
                rs.getString("finished_at"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }

    private int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 || limit > 1000 ? 100 : limit;
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record AlertSnapshot(
            Long id,
            String alertCode,
            String alertLevel,
            String objectKey,
            Long videoId,
            String content
    ) {
    }
}
