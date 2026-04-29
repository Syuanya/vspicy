package com.vspicy.video.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.video.dto.HlsIntegrityItem;
import com.vspicy.video.dto.HlsIntegrityResult;
import com.vspicy.video.dto.HlsRepairVerifyCommand;
import com.vspicy.video.dto.HlsRepairVerifyResult;
import com.vspicy.video.dto.HlsRepairVerifyTaskView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class HlsRepairVerifyService {
    private final JdbcTemplate jdbcTemplate;
    private final HlsIntegrityService hlsIntegrityService;
    private final ObjectMapper objectMapper;

    public HlsRepairVerifyService(
            JdbcTemplate jdbcTemplate,
            HlsIntegrityService hlsIntegrityService,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.hlsIntegrityService = hlsIntegrityService;
        this.objectMapper = objectMapper;
    }

    public HlsRepairVerifyResult preview(Integer limit) {
        int safeLimit = normalizeLimit(limit);
        List<TaskRow> rows = loadVerifyCandidates(safeLimit);

        List<HlsRepairVerifyTaskView> views = new ArrayList<>();
        for (TaskRow row : rows) {
            views.add(new HlsRepairVerifyTaskView(
                    row.id(),
                    row.repairType(),
                    row.status(),
                    row.status(),
                    row.manifestObjectKey(),
                    row.videoId(),
                    row.recordId(),
                    row.traceId(),
                    row.alertId(),
                    null,
                    null,
                    false,
                    "待复检"
            ));
        }

        return new HlsRepairVerifyResult(
                safeLimit,
                true,
                false,
                (long) rows.size(),
                0L,
                0L,
                0L,
                0L,
                "HLS 修复任务复检预览",
                views
        );
    }

    @Transactional
    public HlsRepairVerifyResult verify(HlsRepairVerifyCommand command) {
        int safeLimit = command == null || command.limit() == null ? 10 : normalizeLimit(command.limit());
        boolean dryRun = command == null || command.dryRun() == null || Boolean.TRUE.equals(command.dryRun());
        boolean markFailedOnError = command != null && Boolean.TRUE.equals(command.markFailedOnError());

        List<TaskRow> rows = loadVerifyCandidates(safeLimit);
        return verifyRows(rows, safeLimit, dryRun, markFailedOnError);
    }

    @Transactional
    public HlsRepairVerifyResult verifyOne(Long id, HlsRepairVerifyCommand command) {
        if (id == null) {
            throw new BizException("id 不能为空");
        }

        boolean dryRun = command == null || command.dryRun() == null || Boolean.TRUE.equals(command.dryRun());
        boolean markFailedOnError = command != null && Boolean.TRUE.equals(command.markFailedOnError());

        List<TaskRow> rows = jdbcTemplate.query("""
                SELECT id, repair_type, status, bucket, manifest_object_key,
                       video_id, record_id, trace_id, alert_id
                FROM video_hls_repair_task
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapRow(rs), id);

        if (rows.isEmpty()) {
            throw new BizException("HLS 修复任务不存在");
        }

        return verifyRows(rows, 1, dryRun, markFailedOnError);
    }

    private HlsRepairVerifyResult verifyRows(
            List<TaskRow> rows,
            int limit,
            boolean dryRun,
            boolean markFailedOnError
    ) {
        long ok = 0L;
        long stillBroken = 0L;
        long alertResolved = 0L;
        long failed = 0L;
        List<HlsRepairVerifyTaskView> views = new ArrayList<>();

        for (TaskRow row : rows) {
            try {
                HlsIntegrityResult result = hlsIntegrityService.checkObject(row.manifestObjectKey());
                HlsIntegrityItem item = result.items().isEmpty() ? null : result.items().get(0);

                String verifyStatus = item == null ? "UNKNOWN" : item.status();
                String verifyMessage = item == null ? "未返回复检结果" : item.message();
                String payload = toJson(result);

                if ("HLS_OK".equals(verifyStatus)) {
                    ok++;

                    boolean resolved = false;
                    if (!dryRun) {
                        jdbcTemplate.update("""
                                UPDATE video_hls_repair_task
                                SET status = 'SUCCESS',
                                    verify_status = ?,
                                    verify_message = ?,
                                    verify_payload = ?,
                                    verified_at = NOW(),
                                    finished_at = NOW(),
                                    last_error = NULL
                                WHERE id = ?
                                """, verifyStatus, verifyMessage, payload, row.id());

                        resolved = resolveAlertIfNeeded(row);
                        if (resolved) {
                            alertResolved++;
                        }
                    }

                    views.add(new HlsRepairVerifyTaskView(
                            row.id(),
                            row.repairType(),
                            row.status(),
                            dryRun ? row.status() : "SUCCESS",
                            row.manifestObjectKey(),
                            row.videoId(),
                            row.recordId(),
                            row.traceId(),
                            row.alertId(),
                            verifyStatus,
                            verifyMessage,
                            resolved,
                            dryRun ? "dryRun=true，HLS 已恢复但未更新任务" : "HLS 已恢复，任务标记 SUCCESS"
                    ));
                } else {
                    stillBroken++;

                    if (!dryRun) {
                        if (markFailedOnError) {
                            jdbcTemplate.update("""
                                    UPDATE video_hls_repair_task
                                    SET status = 'FAILED',
                                        verify_status = ?,
                                        verify_message = ?,
                                        verify_payload = ?,
                                        verified_at = NOW(),
                                        finished_at = NOW(),
                                        last_error = ?
                                    WHERE id = ?
                                    """, verifyStatus, verifyMessage, payload, verifyMessage, row.id());
                        } else {
                            jdbcTemplate.update("""
                                    UPDATE video_hls_repair_task
                                    SET verify_status = ?,
                                        verify_message = ?,
                                        verify_payload = ?,
                                        verified_at = NOW(),
                                        last_error = ?
                                    WHERE id = ?
                                    """, verifyStatus, verifyMessage, payload, verifyMessage, row.id());
                        }
                    }

                    views.add(new HlsRepairVerifyTaskView(
                            row.id(),
                            row.repairType(),
                            row.status(),
                            dryRun ? row.status() : (markFailedOnError ? "FAILED" : row.status()),
                            row.manifestObjectKey(),
                            row.videoId(),
                            row.recordId(),
                            row.traceId(),
                            row.alertId(),
                            verifyStatus,
                            verifyMessage,
                            false,
                            dryRun ? "dryRun=true，HLS 仍异常但未更新任务" : "HLS 仍异常"
                    ));
                }
            } catch (Exception ex) {
                failed++;
                String message = "复检失败：" + ex.getMessage();

                if (!dryRun) {
                    jdbcTemplate.update("""
                            UPDATE video_hls_repair_task
                            SET verify_status = 'VERIFY_FAILED',
                                verify_message = ?,
                                verified_at = NOW(),
                                last_error = ?
                            WHERE id = ?
                            """, message, message, row.id());
                }

                views.add(new HlsRepairVerifyTaskView(
                        row.id(),
                        row.repairType(),
                        row.status(),
                        row.status(),
                        row.manifestObjectKey(),
                        row.videoId(),
                        row.recordId(),
                        row.traceId(),
                        row.alertId(),
                        "VERIFY_FAILED",
                        message,
                        false,
                        message
                ));
            }
        }

        return new HlsRepairVerifyResult(
                limit,
                dryRun,
                markFailedOnError,
                (long) rows.size(),
                ok,
                stillBroken,
                alertResolved,
                failed,
                dryRun ? "dryRun=true，仅复检不更新状态" : "HLS 修复任务复检完成",
                views
        );
    }

    private boolean resolveAlertIfNeeded(TaskRow row) {
        if (row.alertId() == null) {
            return false;
        }

        int updated = jdbcTemplate.update("""
                UPDATE video_storage_alert_event
                SET status = 'RESOLVED',
                    resolved_at = NOW()
                WHERE id = ?
                  AND status IN ('OPEN', 'ACKED')
                """, row.alertId());

        return updated > 0;
    }

    private List<TaskRow> loadVerifyCandidates(int limit) {
        return jdbcTemplate.query("""
                SELECT id, repair_type, status, bucket, manifest_object_key,
                       video_id, record_id, trace_id, alert_id
                FROM video_hls_repair_task
                WHERE status IN ('RUNNING', 'DISPATCHED', 'FAILED')
                ORDER BY
                  CASE status
                    WHEN 'RUNNING' THEN 1
                    WHEN 'DISPATCHED' THEN 2
                    WHEN 'FAILED' THEN 3
                    ELSE 4
                  END,
                  id ASC
                LIMIT ?
                """, (rs, rowNum) -> mapRow(rs), limit);
    }

    private TaskRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TaskRow(
                rs.getLong("id"),
                rs.getString("repair_type"),
                rs.getString("status"),
                rs.getString("bucket"),
                rs.getString("manifest_object_key"),
                nullableLong(rs.getObject("video_id")),
                nullableLong(rs.getObject("record_id")),
                nullableLong(rs.getObject("trace_id")),
                nullableLong(rs.getObject("alert_id"))
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 || limit > 100 ? 10 : limit;
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record TaskRow(
            Long id,
            String repairType,
            String status,
            String bucket,
            String manifestObjectKey,
            Long videoId,
            Long recordId,
            Long traceId,
            Long alertId
    ) {
    }
}
