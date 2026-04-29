package com.vspicy.video.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.video.dto.HlsRepairExecuteCommand;
import com.vspicy.video.dto.HlsRepairExecuteResult;
import com.vspicy.video.dto.HlsRepairExecuteTaskView;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class HlsRepairExecuteService {
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    public HlsRepairExecuteService(
            JdbcTemplate jdbcTemplate,
            ApplicationContext applicationContext,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
    }

    public HlsRepairExecuteResult preview(Integer limit) {
        int safeLimit = normalizeLimit(limit);
        List<HlsRepairTaskRow> rows = loadExecutableTasks(safeLimit, false);
        List<ExecutorCandidate> candidates = findExecutorCandidates();

        List<HlsRepairExecuteTaskView> views = new ArrayList<>();
        for (HlsRepairTaskRow row : rows) {
            ExecutorCandidate candidate = candidates.isEmpty() ? null : candidates.get(0);
            views.add(new HlsRepairExecuteTaskView(
                    row.id(),
                    row.repairType(),
                    row.status(),
                    row.manifestObjectKey(),
                    row.videoId(),
                    row.recordId(),
                    row.traceId(),
                    candidate == null ? null : candidate.beanName(),
                    candidate == null ? null : candidate.method().getName(),
                    candidate == null ? "未找到兼容转码执行 Bean" : "可尝试执行"
            ));
        }

        return new HlsRepairExecuteResult(
                safeLimit,
                true,
                false,
                (long) rows.size(),
                0L,
                (long) rows.size(),
                0L,
                "HLS 修复任务执行预览",
                views
        );
    }

    @Transactional
    public HlsRepairExecuteResult execute(HlsRepairExecuteCommand command) {
        int safeLimit = command == null || command.limit() == null ? 5 : normalizeLimit(command.limit());
        boolean dryRun = command == null || command.dryRun() == null || Boolean.TRUE.equals(command.dryRun());
        boolean allowPending = command != null && Boolean.TRUE.equals(command.allowPending());

        List<HlsRepairTaskRow> rows = loadExecutableTasks(safeLimit, allowPending);
        return executeRows(rows, safeLimit, dryRun, allowPending);
    }

    @Transactional
    public HlsRepairExecuteResult executeOne(Long id, HlsRepairExecuteCommand command) {
        if (id == null) {
            throw new BizException("id 不能为空");
        }

        boolean dryRun = command == null || command.dryRun() == null || Boolean.TRUE.equals(command.dryRun());
        boolean allowPending = command != null && Boolean.TRUE.equals(command.allowPending());

        List<HlsRepairTaskRow> rows = jdbcTemplate.query("""
                SELECT id, repair_type, status, priority, bucket,
                       manifest_object_key, missing_segments, missing_segment_count,
                       video_id, record_id, trace_id, alert_id, source,
                       retry_count, max_retry_count
                FROM video_hls_repair_task
                WHERE id = ?
                  AND status IN ('DISPATCHED', 'PENDING')
                LIMIT 1
                """, (rs, rowNum) -> mapRow(rs), id);

        if (rows.isEmpty()) {
            throw new BizException("任务不存在或状态不可执行");
        }

        if ("PENDING".equals(rows.get(0).status()) && !allowPending) {
            throw new BizException("任务仍为 PENDING，如需跳过分发直接执行，请设置 allowPending=true");
        }

        return executeRows(rows, 1, dryRun, allowPending);
    }

    public void executeFromMessage(Map<String, Object> message) {
        Long taskId = readLong(message == null ? null : message.get("taskId"));
        if (taskId == null) {
            throw new BizException("HLS_REPAIR 消息缺少 taskId");
        }

        executeOne(taskId, new HlsRepairExecuteCommand(1, false, false));
    }

    private HlsRepairExecuteResult executeRows(
            List<HlsRepairTaskRow> rows,
            int limit,
            boolean dryRun,
            boolean allowPending
    ) {
        List<ExecutorCandidate> candidates = findExecutorCandidates();
        ExecutorCandidate candidate = candidates.isEmpty() ? null : candidates.get(0);

        long executed = 0L;
        long skipped = 0L;
        long failed = 0L;
        List<HlsRepairExecuteTaskView> views = new ArrayList<>();

        for (HlsRepairTaskRow row : rows) {
            Map<String, Object> payload = buildPayload(row);
            String payloadText = toJson(payload);

            if (dryRun) {
                skipped++;
                views.add(view(row, candidate, "dryRun=true，未执行"));
                continue;
            }

            if (candidate == null) {
                skipped++;
                jdbcTemplate.update("""
                        UPDATE video_hls_repair_task
                        SET last_error = ?,
                            execute_payload = ?
                        WHERE id = ?
                        """, "未找到兼容转码执行 Bean，任务保持 " + row.status(), payloadText, row.id());
                views.add(view(row, null, "未找到兼容转码执行 Bean，任务保持 " + row.status()));
                continue;
            }

            try {
                invokeCandidate(candidate, row, payload);

                int updated = jdbcTemplate.update("""
                        UPDATE video_hls_repair_task
                        SET status = 'RUNNING',
                            execute_mode = 'REFLECTION_BEAN',
                            execute_payload = ?,
                            executor_bean = ?,
                            executor_method = ?,
                            started_at = NOW(),
                            last_error = NULL
                        WHERE id = ?
                          AND status IN ('DISPATCHED', 'PENDING')
                        """, payloadText, candidate.beanName(), candidate.method().getName(), row.id());

                if (updated > 0) {
                    executed++;
                    views.add(view(row, candidate, "已调用转码执行 Bean，任务标记 RUNNING"));
                } else {
                    skipped++;
                    views.add(view(row, candidate, "任务状态已变化，跳过"));
                }
            } catch (Exception ex) {
                failed++;
                jdbcTemplate.update("""
                        UPDATE video_hls_repair_task
                        SET last_error = ?,
                            execute_payload = ?
                        WHERE id = ?
                        """, "执行失败：" + ex.getMessage(), payloadText, row.id());
                views.add(view(row, candidate, "执行失败：" + ex.getMessage()));
            }
        }

        return new HlsRepairExecuteResult(
                limit,
                dryRun,
                allowPending,
                (long) rows.size(),
                executed,
                skipped,
                failed,
                dryRun ? "dryRun=true，仅预览未执行" : "HLS 修复任务执行完成",
                views
        );
    }

    private void invokeCandidate(ExecutorCandidate candidate, HlsRepairTaskRow row, Map<String, Object> payload) throws Exception {
        Method method = candidate.method();
        Object bean = candidate.bean();

        if (method.getParameterCount() == 0) {
            method.invoke(bean);
            return;
        }

        Class<?> parameterType = method.getParameterTypes()[0];

        if (Map.class.isAssignableFrom(parameterType)) {
            method.invoke(bean, payload);
            return;
        }

        if (Long.class == parameterType || long.class == parameterType) {
            Long value = row.videoId() != null ? row.videoId() : row.id();
            method.invoke(bean, value);
            return;
        }

        if (String.class == parameterType) {
            method.invoke(bean, row.manifestObjectKey());
            return;
        }

        throw new IllegalStateException("暂不支持的执行方法参数类型：" + parameterType.getName());
    }

    private List<HlsRepairTaskRow> loadExecutableTasks(int limit, boolean allowPending) {
        String statusSql = allowPending ? "('DISPATCHED', 'PENDING')" : "('DISPATCHED')";

        return jdbcTemplate.query("""
                SELECT id, repair_type, status, priority, bucket,
                       manifest_object_key, missing_segments, missing_segment_count,
                       video_id, record_id, trace_id, alert_id, source,
                       retry_count, max_retry_count
                FROM video_hls_repair_task
                WHERE status IN %s
                  AND retry_count <= max_retry_count
                ORDER BY priority ASC, id ASC
                LIMIT ?
                """.formatted(statusSql), (rs, rowNum) -> mapRow(rs), limit);
    }

    private HlsRepairTaskRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new HlsRepairTaskRow(
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
        );
    }

    private List<ExecutorCandidate> findExecutorCandidates() {
        List<ExecutorCandidate> result = new ArrayList<>();
        String[] names = applicationContext.getBeanDefinitionNames();

        for (String beanName : names) {
            String lowerName = beanName.toLowerCase(Locale.ROOT);
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception ignored) {
                continue;
            }

            Class<?> beanClass = bean.getClass();
            String lowerClassName = beanClass.getName().toLowerCase(Locale.ROOT);

            if (!(lowerName.contains("transcode") || lowerClassName.contains("transcode"))) {
                continue;
            }

            if (lowerClassName.contains("hlsrepairexecute")) {
                continue;
            }

            for (Method method : beanClass.getMethods()) {
                if (method.getDeclaringClass() == Object.class) {
                    continue;
                }

                if (method.getParameterCount() > 1) {
                    continue;
                }

                String methodName = method.getName().toLowerCase(Locale.ROOT);
                boolean action = methodName.contains("dispatch")
                        || methodName.contains("submit")
                        || methodName.contains("send")
                        || methodName.contains("start")
                        || methodName.contains("create");
                boolean target = methodName.contains("transcode")
                        || methodName.contains("task")
                        || methodName.contains("repair");

                if (action && target) {
                    result.add(new ExecutorCandidate(beanName, bean, method));
                }
            }
        }

        result.sort(Comparator.comparing(ExecutorCandidate::beanName).thenComparing(c -> c.method().getName()));
        return result;
    }

    private Map<String, Object> buildPayload(HlsRepairTaskRow row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "HLS_REPAIR_EXECUTE");
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
        payload.put("createdAt", LocalDateTime.now().toString());
        return payload;
    }

    private HlsRepairExecuteTaskView view(HlsRepairTaskRow row, ExecutorCandidate candidate, String message) {
        return new HlsRepairExecuteTaskView(
                row.id(),
                row.repairType(),
                row.status(),
                row.manifestObjectKey(),
                row.videoId(),
                row.recordId(),
                row.traceId(),
                candidate == null ? null : candidate.beanName(),
                candidate == null ? null : candidate.method().getName(),
                message
        );
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

    private Long readLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
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

    private record ExecutorCandidate(
            String beanName,
            Object bean,
            Method method
    ) {
    }
}
