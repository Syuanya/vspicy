package com.vspicy.video.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.dto.OperationAuditCommand;
import com.vspicy.video.dto.OperationAuditLogView;
import com.vspicy.video.dto.OperationAuditStatsView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OperationAuditService {
    private final JdbcTemplate jdbcTemplate;

    public OperationAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OperationAuditLogView record(OperationAuditCommand command, HttpServletRequest request) {
        if (!tableExists("operation_audit_log")) {
            throw new BizException("operation_audit_log 表不存在，请先执行 sql/72-vspicy-operation-audit-log.sql");
        }

        if (command == null || isBlank(command.action())) {
            throw new BizException("action 不能为空");
        }

        String ip = clientIp(request);
        String userAgent = request == null ? null : request.getHeader("User-Agent");

        jdbcTemplate.update("""
                INSERT INTO operation_audit_log(
                  action, target_type, target_id, operator_id, operator_name,
                  description, request_ip, user_agent, detail_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                command.action(),
                command.targetType(),
                command.targetId(),
                command.operatorId(),
                command.operatorName(),
                command.description(),
                ip,
                userAgent,
                command.detailJson()
        );

        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return findById(id);
    }

    public void recordQuietly(
            String action,
            String targetType,
            String targetId,
            Long operatorId,
            String operatorName,
            String description,
            String detailJson
    ) {
        try {
            record(new OperationAuditCommand(action, targetType, targetId, operatorId, operatorName, description, detailJson), null);
        } catch (Exception ignored) {
            // 审计日志不能影响主业务
        }
    }

    public List<OperationAuditLogView> list(
            String action,
            String targetType,
            Long operatorId,
            Integer limit
    ) {
        if (!tableExists("operation_audit_log")) {
            return List.of();
        }

        int safeLimit = normalizeLimit(limit);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, action, target_type, target_id, operator_id, operator_name,
                       description, request_ip, user_agent, detail_json, created_at
                FROM operation_audit_log
                WHERE 1 = 1
                """);

        if (!isBlank(action)) {
            sql.append(" AND action = ?");
            args.add(action);
        }

        if (!isBlank(targetType)) {
            sql.append(" AND target_type = ?");
            args.add(targetType);
        }

        if (operatorId != null) {
            sql.append(" AND operator_id = ?");
            args.add(operatorId);
        }

        sql.append(" ORDER BY id DESC LIMIT ?");
        args.add(safeLimit);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new OperationAuditLogView(
                rs.getLong("id"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                nullableLong(rs.getObject("operator_id")),
                rs.getString("operator_name"),
                rs.getString("description"),
                rs.getString("request_ip"),
                rs.getString("user_agent"),
                rs.getString("detail_json"),
                rs.getString("created_at")
        ), args.toArray());
    }

    public OperationAuditStatsView stats() {
        if (!tableExists("operation_audit_log")) {
            return new OperationAuditStatsView(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        return new OperationAuditStatsView(
                countAll(),
                countToday(),
                countActionPrefix("TRANSCODE_"),
                countActionPrefix("PLAYBACK_"),
                countActionPrefix("CLEANUP_"),
                countActionPrefix("HLS_REPAIR_"),
                countActionPrefix("STORAGE_"),
                countRejectedActions(),
                countDangerActions(),
                countSuccessActions()
        );
    }

    private OperationAuditLogView findById(Long id) {
        List<OperationAuditLogView> rows = jdbcTemplate.query("""
                SELECT id, action, target_type, target_id, operator_id, operator_name,
                       description, request_ip, user_agent, detail_json, created_at
                FROM operation_audit_log
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> new OperationAuditLogView(
                rs.getLong("id"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                nullableLong(rs.getObject("operator_id")),
                rs.getString("operator_name"),
                rs.getString("description"),
                rs.getString("request_ip"),
                rs.getString("user_agent"),
                rs.getString("detail_json"),
                rs.getString("created_at")
        ), id);

        if (rows.isEmpty()) {
            throw new BizException("审计日志不存在：" + id);
        }

        return rows.get(0);
    }

    private Long countAll() {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_audit_log", Long.class);
        return value == null ? 0L : value;
    }

    private Long countToday() {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM operation_audit_log
                WHERE created_at >= CURDATE()
                """, Long.class);
        return value == null ? 0L : value;
    }

    private Long countActionPrefix(String prefix) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_audit_log WHERE action LIKE ?",
                Long.class,
                prefix + "%"
        );
        return value == null ? 0L : value;
    }


    private Long countRejectedActions() {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM operation_audit_log
                WHERE RIGHT(action, 9) = '_REJECTED'
                   OR REPLACE(detail_json, ' ', '') LIKE '%\"rejected\":true%'
                """, Long.class);
        return value == null ? 0L : value;
    }

    private Long countDangerActions() {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM operation_audit_log
                WHERE RIGHT(action, 9) = '_REJECTED'
                   OR action LIKE '%RERUN%'
                   OR action LIKE '%RESET%'
                   OR action LIKE '%CANCEL%'
                   OR action LIKE '%FAIL%'
                   OR action LIKE '%SYNC%'
                   OR action LIKE '%CLEANUP%'
                   OR action LIKE '%REPAIR%'
                """, Long.class);
        return value == null ? 0L : value;
    }

    private Long countSuccessActions() {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM operation_audit_log
                WHERE RIGHT(action, 9) <> '_REJECTED'
                  AND action NOT LIKE '%FAILED%'
                  AND action NOT LIKE '%FAIL%'
                """, Long.class);
        return value == null ? 0L : value;
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

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 100;
        }
        return Math.min(limit, 500);
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (!isBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (!isBlank(realIp)) {
            return realIp;
        }

        return request.getRemoteAddr();
    }
}
