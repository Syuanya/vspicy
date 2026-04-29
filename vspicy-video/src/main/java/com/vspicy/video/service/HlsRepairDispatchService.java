package com.vspicy.video.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vspicy.video.dto.HlsRepairDispatchCommand;
import com.vspicy.video.dto.HlsRepairDispatchResult;
import com.vspicy.video.dto.HlsRepairDispatchTaskView;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class HlsRepairDispatchService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final String tag;

    public HlsRepairDispatchService(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider,
            ObjectMapper objectMapper,
            @Value("${vspicy.video.hls.repair.dispatch.topic:vspicy-video-hls-repair-topic}") String topic,
            @Value("${vspicy.video.hls.repair.dispatch.tag:HLS_REPAIR}") String tag
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.rocketMQTemplateProvider = rocketMQTemplateProvider;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.tag = tag;
    }

    public HlsRepairDispatchResult preview(Integer limit) {
        int safeLimit = normalizeLimit(limit);
        List<HlsRepairTaskRow> rows = loadPendingTasks(safeLimit);
        List<HlsRepairDispatchTaskView> tasks = rows.stream()
                .map(row -> new HlsRepairDispatchTaskView(
                        row.id(),
                        row.repairType(),
                        row.status(),
                        row.manifestObjectKey(),
                        row.videoId(),
                        row.recordId(),
                        row.traceId(),
                        row.retryCount(),
                        row.maxRetryCount(),
                        topic,
                        tag,
                        "待分发"
                ))
                .toList();

        return new HlsRepairDispatchResult(
                safeLimit,
                true,
                topic,
                tag,
                rocketMQTemplateProvider.getIfAvailable() != null,
                (long) rows.size(),
                0L,
                0L,
                0L,
                "HLS 修复任务分发预览",
                tasks
        );
    }

    @Transactional
    public HlsRepairDispatchResult dispatch(HlsRepairDispatchCommand command) {
        int safeLimit = command == null || command.limit() == null ? 10 : normalizeLimit(command.limit());
        boolean dryRun = command == null || command.dryRun() == null || Boolean.TRUE.equals(command.dryRun());

        List<HlsRepairTaskRow> rows = loadPendingTasks(safeLimit);
        RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        boolean rocketMqAvailable = rocketMQTemplate != null;

        long dispatched = 0L;
        long skipped = 0L;
        long failed = 0L;
        List<HlsRepairDispatchTaskView> taskViews = new ArrayList<>();

        for (HlsRepairTaskRow row : rows) {
            Map<String, Object> payload = buildPayload(row);
            String payloadText = toJson(payload);

            if (dryRun) {
                skipped++;
                taskViews.add(view(row, "dryRun=true，未发送 MQ"));
                continue;
            }

            if (!rocketMqAvailable) {
                skipped++;
                jdbcTemplate.update("""
                        UPDATE video_hls_repair_task
                        SET last_error = ?
                        WHERE id = ?
                          AND status = 'PENDING'
                        """, "RocketMQTemplate 不存在，无法分发", row.id());
                taskViews.add(view(row, "RocketMQTemplate 不存在，任务保持 PENDING"));
                continue;
            }

            try {
                String destination = topic + ":" + tag;
                rocketMQTemplate.syncSend(destination, MessageBuilder.withPayload(payload).build());

                int updated = jdbcTemplate.update("""
                        UPDATE video_hls_repair_task
                        SET status = 'DISPATCHED',
                            dispatch_topic = ?,
                            dispatch_tag = ?,
                            dispatch_payload = ?,
                            dispatch_message_id = ?,
                            dispatched_at = NOW(),
                            last_error = NULL
                        WHERE id = ?
                          AND status = 'PENDING'
                        """, topic, tag, payloadText, UUID.randomUUID().toString(), row.id());

                if (updated > 0) {
                    dispatched++;
                    taskViews.add(view(row, "已分发到 " + destination));
                } else {
                    skipped++;
                    taskViews.add(view(row, "任务状态已变化，跳过"));
                }
            } catch (Exception ex) {
                failed++;
                jdbcTemplate.update("""
                        UPDATE video_hls_repair_task
                        SET last_error = ?
                        WHERE id = ?
                          AND status = 'PENDING'
                        """, "分发失败：" + ex.getMessage(), row.id());
                taskViews.add(view(row, "分发失败：" + ex.getMessage()));
            }
        }

        return new HlsRepairDispatchResult(
                safeLimit,
                dryRun,
                topic,
                tag,
                rocketMqAvailable,
                (long) rows.size(),
                dispatched,
                skipped,
                failed,
                dryRun ? "dryRun=true，仅预览未分发" : "HLS 修复任务分发完成",
                taskViews
        );
    }

    private List<HlsRepairTaskRow> loadPendingTasks(int limit) {
        return jdbcTemplate.query("""
                SELECT id, repair_type, status, priority, bucket,
                       manifest_object_key, missing_segments, missing_segment_count,
                       video_id, record_id, trace_id, alert_id, source,
                       retry_count, max_retry_count
                FROM video_hls_repair_task
                WHERE status = 'PENDING'
                  AND retry_count <= max_retry_count
                ORDER BY priority ASC, id ASC
                LIMIT ?
                """, (rs, rowNum) -> new HlsRepairTaskRow(
                rs.getLong("id"),
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
                rs.getInt("max_retry_count")
        ), limit);
    }

    private Map<String, Object> buildPayload(HlsRepairTaskRow row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "HLS_REPAIR");
        payload.put("taskId", row.id());
        payload.put("repairType", row.repairType());
        payload.put("bucket", row.bucket());
        payload.put("manifestObjectKey", row.manifestObjectKey());
        payload.put("missingSegments", splitSegments(row.missingSegments()));
        payload.put("missingSegmentCount", row.missingSegmentCount());
        payload.put("videoId", row.videoId());
        payload.put("recordId", row.recordId());
        payload.put("traceId", row.traceId());
        payload.put("alertId", row.alertId());
        payload.put("source", row.source());
        payload.put("retryCount", row.retryCount());
        payload.put("createdAt", LocalDateTime.now().toString());
        return payload;
    }

    private List<String> splitSegments(String segments) {
        if (segments == null || segments.isBlank()) {
            return List.of();
        }
        return Arrays.stream(segments.split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private HlsRepairDispatchTaskView view(HlsRepairTaskRow row, String message) {
        return new HlsRepairDispatchTaskView(
                row.id(),
                row.repairType(),
                row.status(),
                row.manifestObjectKey(),
                row.videoId(),
                row.recordId(),
                row.traceId(),
                row.retryCount(),
                row.maxRetryCount(),
                topic,
                tag,
                message
        );
    }

    private int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 || limit > 100 ? 10 : limit;
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record HlsRepairTaskRow(
            Long id,
            String repairType,
            String status,
            Integer priority,
            String bucket,
            String manifestObjectKey,
            String missingSegments,
            Integer missingSegmentCount,
            Long videoId,
            Long recordId,
            Long traceId,
            Long alertId,
            String source,
            Integer retryCount,
            Integer maxRetryCount
    ) {
    }
}
