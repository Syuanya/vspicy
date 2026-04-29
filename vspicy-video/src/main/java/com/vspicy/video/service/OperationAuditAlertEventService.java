package com.vspicy.video.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.dto.OperationAuditAlertEventSummaryView;
import com.vspicy.video.dto.OperationAuditAlertEventSyncCommand;
import com.vspicy.video.dto.OperationAuditAlertEventSyncResult;
import com.vspicy.video.dto.OperationAuditAlertEventView;
import com.vspicy.video.dto.OperationAuditAlertView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OperationAuditAlertEventService {
    private static final String TABLE_NAME = "operation_audit_alert_event";

    private final JdbcTemplate jdbcTemplate;
    private final OperationAuditEnhancedService enhancedService;

    public OperationAuditAlertEventService(
            JdbcTemplate jdbcTemplate,
            OperationAuditEnhancedService enhancedService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.enhancedService = enhancedService;
    }

    public List<OperationAuditAlertEventView> list(String status, String level, String alertType, Integer limit) {
        if (!tableExists(TABLE_NAME)) {
            return List.of();
        }

        String safeStatus = isBlank(status) ? "OPEN" : status.trim().toUpperCase();
        String safeLevel = isBlank(level) ? "" : level.trim().toUpperCase();
        String safeAlertType = isBlank(alertType) ? "" : alertType.trim();
        int safeLimit = normalizeLimit(limit, 100, 500);

        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, dedup_key, alert_type, alert_level, title, message,
                       action, target_type, target_id, operator_id, operator_name, request_ip,
                       alert_count, evidence_audit_ids, link, status,
                       first_seen_at, last_seen_at, acked_at, resolved_at, created_at, updated_at
                FROM operation_audit_alert_event
                WHERE 1 = 1
                """);

        if (!"ALL".equals(safeStatus)) {
            sql.append(" AND status = ?");
            args.add(safeStatus);
        }

        if (!safeLevel.isBlank()) {
            sql.append(" AND alert_level = ?");
            args.add(safeLevel);
        }

        if (!safeAlertType.isBlank()) {
            sql.append(" AND alert_type = ?");
            args.add(safeAlertType);
        }

        sql.append("""
                ORDER BY
                  CASE alert_level
                    WHEN 'CRITICAL' THEN 1
                    WHEN 'DANGER' THEN 2
                    WHEN 'WARNING' THEN 3
                    WHEN 'INFO' THEN 4
                    ELSE 5
                  END,
                  last_seen_at DESC,
                  id DESC
                LIMIT ?
                """);
        args.add(safeLimit);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapEvent(rs), args.toArray());
    }

    public OperationAuditAlertEventSummaryView summary() {
        if (!tableExists(TABLE_NAME)) {
            return new OperationAuditAlertEventSummaryView(
                    LocalDateTime.now().toString(),
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    "SUCCESS"
            );
        }

        long open = count("status = 'OPEN'");
        long acked = count("status = 'ACKED'");
        long resolved = count("status = 'RESOLVED'");
        long criticalOpen = count("status = 'OPEN' AND alert_level = 'CRITICAL'");
        long dangerOpen = count("status = 'OPEN' AND alert_level = 'DANGER'");
        long warningOpen = count("status = 'OPEN' AND alert_level = 'WARNING'");
        long total = count("1 = 1");

        return new OperationAuditAlertEventSummaryView(
                LocalDateTime.now().toString(),
                open,
                acked,
                resolved,
                criticalOpen,
                dangerOpen,
                warningOpen,
                total,
                highestLevel(criticalOpen, dangerOpen, warningOpen, open)
        );
    }

    @Transactional
    public OperationAuditAlertEventSyncResult sync(OperationAuditAlertEventSyncCommand command) {
        ensureTable();

        int hours = normalizeHours(command == null ? null : command.hours());
        int limit = normalizeLimit(command == null ? null : command.limit(), 100, 300);
        List<OperationAuditAlertView> alerts = enhancedService.alerts(hours, limit);

        long generated = 0L;
        for (OperationAuditAlertView alert : alerts) {
            upsertAlert(alert);
            generated++;
        }

        return new OperationAuditAlertEventSyncResult(
                hours,
                limit,
                generated,
                count("status = 'OPEN'"),
                count("status = 'ACKED'"),
                count("status = 'RESOLVED'"),
                "操作审计告警已同步到告警收件箱"
        );
    }

    @Transactional
    public OperationAuditAlertEventView ack(Long id) {
        ensureTable();
        if (id == null) {
            throw new BizException("id 不能为空");
        }

        int updated = jdbcTemplate.update("""
                UPDATE operation_audit_alert_event
                SET status = 'ACKED',
                    acked_at = NOW(),
                    updated_at = NOW()
                WHERE id = ?
                  AND status = 'OPEN'
                """, id);

        if (updated <= 0) {
            throw new BizException("告警不存在或当前状态不可确认");
        }

        return findById(id);
    }

    @Transactional
    public OperationAuditAlertEventView resolve(Long id) {
        ensureTable();
        if (id == null) {
            throw new BizException("id 不能为空");
        }

        int updated = jdbcTemplate.update("""
                UPDATE operation_audit_alert_event
                SET status = 'RESOLVED',
                    resolved_at = NOW(),
                    updated_at = NOW()
                WHERE id = ?
                  AND status IN ('OPEN', 'ACKED')
                """, id);

        if (updated <= 0) {
            throw new BizException("告警不存在或当前状态不可解决");
        }

        return findById(id);
    }

    private void upsertAlert(OperationAuditAlertView alert) {
        String dedupKey = buildDedupKey(alert);
        String title = alert.alertType();
        String level = normalizeLevel(alert.alertLevel());
        String evidenceIds = encodeIds(alert.evidenceAuditIds());

        jdbcTemplate.update("""
                INSERT INTO operation_audit_alert_event(
                  dedup_key, alert_type, alert_level, title, message,
                  action, target_type, target_id, operator_id, operator_name, request_ip,
                  alert_count, evidence_audit_ids, link, status,
                  first_seen_at, last_seen_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', NOW(), NOW())
                ON DUPLICATE KEY UPDATE
                  alert_type = VALUES(alert_type),
                  alert_level = VALUES(alert_level),
                  title = VALUES(title),
                  message = VALUES(message),
                  action = VALUES(action),
                  target_type = VALUES(target_type),
                  target_id = VALUES(target_id),
                  operator_id = VALUES(operator_id),
                  operator_name = VALUES(operator_name),
                  request_ip = VALUES(request_ip),
                  alert_count = VALUES(alert_count),
                  evidence_audit_ids = VALUES(evidence_audit_ids),
                  link = VALUES(link),
                  last_seen_at = NOW(),
                  acked_at = CASE WHEN status = 'RESOLVED' THEN NULL ELSE acked_at END,
                  resolved_at = CASE WHEN status = 'RESOLVED' THEN NULL ELSE resolved_at END,
                  status = CASE WHEN status = 'RESOLVED' THEN 'OPEN' ELSE status END,
                  updated_at = NOW()
                """,
                dedupKey,
                alert.alertType(),
                level,
                title,
                alert.message(),
                alert.action(),
                alert.targetType(),
                alert.targetId(),
                alert.operatorId(),
                alert.operatorName(),
                alert.requestIp(),
                alert.count() == null ? 0L : alert.count(),
                evidenceIds,
                alert.link()
        );
    }

    private OperationAuditAlertEventView findById(Long id) {
        List<OperationAuditAlertEventView> rows = jdbcTemplate.query("""
                SELECT id, dedup_key, alert_type, alert_level, title, message,
                       action, target_type, target_id, operator_id, operator_name, request_ip,
                       alert_count, evidence_audit_ids, link, status,
                       first_seen_at, last_seen_at, acked_at, resolved_at, created_at, updated_at
                FROM operation_audit_alert_event
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapEvent(rs), id);

        if (rows.isEmpty()) {
            throw new BizException("告警不存在");
        }
        return rows.get(0);
    }

    private OperationAuditAlertEventView mapEvent(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OperationAuditAlertEventView(
                rs.getLong("id"),
                rs.getString("dedup_key"),
                rs.getString("alert_type"),
                rs.getString("alert_level"),
                rs.getString("title"),
                rs.getString("message"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                nullableLong(rs.getObject("operator_id")),
                rs.getString("operator_name"),
                rs.getString("request_ip"),
                nullableLong(rs.getObject("alert_count")),
                parseIds(rs.getString("evidence_audit_ids"), 30),
                rs.getString("link"),
                rs.getString("status"),
                rs.getString("first_seen_at"),
                rs.getString("last_seen_at"),
                rs.getString("acked_at"),
                rs.getString("resolved_at"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }

    private String buildDedupKey(OperationAuditAlertView alert) {
        List<String> parts = new ArrayList<>();
        parts.add(nullToDash(alert.alertType()));
        parts.add(nullToDash(alert.action()));
        parts.add(nullToDash(alert.targetType()));
        parts.add(nullToDash(alert.targetId()));
        parts.add(alert.operatorId() == null ? "-" : String.valueOf(alert.operatorId()));
        parts.add(nullToDash(alert.operatorName()));
        parts.add(nullToDash(alert.requestIp()));
        return limitLength(parts.stream().collect(Collectors.joining(":")), 500);
    }

    private String encodeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return ids.stream().limit(30).map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> parseIds(String value, int max) {
        if (isBlank(value)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : value.split(",")) {
            if (ids.size() >= max) {
                break;
            }
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (Exception ignored) {
                // skip invalid id
            }
        }
        return ids;
    }

    private String normalizeLevel(String level) {
        if (isBlank(level)) {
            return "WARNING";
        }
        String value = level.trim().toUpperCase();
        if ("CRITICAL".equals(value) || "DANGER".equals(value) || "WARNING".equals(value) || "INFO".equals(value)) {
            return value;
        }
        return "WARNING";
    }

    private String highestLevel(long critical, long danger, long warning, long open) {
        if (critical > 0) {
            return "CRITICAL";
        }
        if (danger > 0) {
            return "DANGER";
        }
        if (warning > 0) {
            return "WARNING";
        }
        if (open > 0) {
            return "INFO";
        }
        return "SUCCESS";
    }

    private long count(String predicate) {
        if (!tableExists(TABLE_NAME)) {
            return 0L;
        }
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_audit_alert_event WHERE " + predicate,
                Long.class
        );
        return value == null ? 0L : value;
    }

    private void ensureTable() {
        if (!tableExists(TABLE_NAME)) {
            throw new BizException("operation_audit_alert_event 表不存在，请先执行 docs/sql/84-vspicy-operation-audit-alert-event.sql");
        }
    }

    private boolean tableExists(String tableName) {
        try {
            Long value = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                    """, Long.class, tableName);
            return value != null && value > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private int normalizeHours(Integer hours) {
        if (hours == null || hours <= 0) {
            return 24;
        }
        return Math.min(hours, 168);
    }

    private int normalizeLimit(Integer limit, int defaultValue, int maxValue) {
        if (limit == null || limit <= 0) {
            return defaultValue;
        }
        return Math.min(limit, maxValue);
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private String limitLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String nullToDash(String value) {
        return isBlank(value) ? "-" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
