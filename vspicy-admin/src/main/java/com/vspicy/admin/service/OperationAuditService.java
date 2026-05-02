package com.vspicy.admin.service;

import com.vspicy.admin.dto.OperationAuditCleanupCommand;
import com.vspicy.admin.dto.OperationAuditCleanupPreviewView;
import com.vspicy.admin.dto.OperationAuditCreateCommand;
import com.vspicy.admin.dto.OperationAuditMetricItem;
import com.vspicy.admin.dto.OperationAuditOverviewView;
import com.vspicy.admin.dto.OperationAuditView;
import com.vspicy.common.exception.BizException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class OperationAuditService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ACTION_TYPES = Set.of("CREATE", "UPDATE", "DELETE", "STATUS", "HANDLE", "LOGIN", "EXPORT", "IMPORT", "OTHER");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> HANDLE_STATUSES = Set.of("OPEN", "REVIEWED", "IGNORED");

    private final JdbcTemplate jdbcTemplate;

    public OperationAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OperationAuditOverviewView overview() {
        long total = count("SELECT COUNT(*) FROM sys_operation_audit_log", List.of());
        long success = count("SELECT COUNT(*) FROM sys_operation_audit_log WHERE success = 1", List.of());
        long failed = count("SELECT COUNT(*) FROM sys_operation_audit_log WHERE success = 0", List.of());
        long highRisk = count("SELECT COUNT(*) FROM sys_operation_audit_log WHERE risk_level IN ('HIGH', 'CRITICAL')", List.of());
        long today = count("SELECT COUNT(*) FROM sys_operation_audit_log WHERE DATE(created_at) = CURDATE()", List.of());
        long openFailed = count("SELECT COUNT(*) FROM sys_operation_audit_log WHERE success = 0 AND handle_status = 'OPEN'", List.of());
        return new OperationAuditOverviewView(
                total,
                success,
                failed,
                highRisk,
                today,
                openFailed,
                distribution("service_name"),
                distribution("action_type"),
                distribution("risk_level"),
                distribution("handle_status")
        );
    }

    public List<OperationAuditView> list(
            String serviceName,
            String actionType,
            String riskLevel,
            String handleStatus,
            Boolean success,
            Long operatorId,
            String keyword,
            Integer limit
    ) {
        int safeLimit = limit == null || limit <= 0 || limit > 1000 ? 100 : limit;
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");

        if (hasText(serviceName)) {
            where.append(" AND service_name = ? ");
            params.add(serviceName.trim());
        }
        if (hasText(actionType)) {
            where.append(" AND action_type = ? ");
            params.add(normalizeAction(actionType));
        }
        if (hasText(riskLevel)) {
            where.append(" AND risk_level = ? ");
            params.add(normalizeRisk(riskLevel));
        }
        if (hasText(handleStatus)) {
            where.append(" AND handle_status = ? ");
            params.add(normalizeHandleStatus(handleStatus));
        }
        if (success != null) {
            where.append(" AND success = ? ");
            params.add(success ? 1 : 0);
        }
        if (operatorId != null) {
            where.append(" AND operator_id = ? ");
            params.add(operatorId);
        }
        if (hasText(keyword)) {
            where.append("""
                    AND (
                      trace_id LIKE ?
                      OR module_name LIKE ?
                      OR operation_name LIKE ?
                      OR request_uri LIKE ?
                      OR operator_name LIKE ?
                      OR operator_ip LIKE ?
                      OR error_message LIKE ?
                    )
                    """);
            String like = "%" + keyword.trim() + "%";
            for (int i = 0; i < 7; i++) {
                params.add(like);
            }
        }
        params.add(safeLimit);

        return jdbcTemplate.query("""
                SELECT id, trace_id, service_name, module_name, action_type, operation_name,
                       request_method, request_uri, request_params, request_body, response_status,
                       success, error_message, risk_level, handle_status, handle_remark,
                       operator_id, operator_name, operator_ip, user_agent, cost_ms,
                       handled_by, handled_at, created_at
                FROM sys_operation_audit_log
                """ + where + " ORDER BY id DESC LIMIT ?", this::mapView, params.toArray());
    }

    public OperationAuditView get(Long id) {
        if (id == null) {
            throw new BizException("审计日志ID不能为空");
        }
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, trace_id, service_name, module_name, action_type, operation_name,
                           request_method, request_uri, request_params, request_body, response_status,
                           success, error_message, risk_level, handle_status, handle_remark,
                           operator_id, operator_name, operator_ip, user_agent, cost_ms,
                           handled_by, handled_at, created_at
                    FROM sys_operation_audit_log
                    WHERE id = ?
                    """, this::mapView, id);
        } catch (EmptyResultDataAccessException ignored) {
            throw new BizException("审计日志不存在");
        }
    }

    @Transactional
    public OperationAuditView create(OperationAuditCreateCommand command) {
        if (command == null) {
            throw new BizException("审计记录不能为空");
        }
        String operationName = trimToNull(command.operationName());
        if (operationName == null) {
            throw new BizException("操作名称不能为空");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO sys_operation_audit_log(
                      trace_id, service_name, module_name, action_type, operation_name,
                      request_method, request_uri, request_params, request_body, response_status,
                      success, error_message, risk_level, handle_status, operator_id, operator_name,
                      operator_ip, user_agent, cost_ms
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, defaultString(command.traceId(), UUID.randomUUID().toString()));
            ps.setString(2, defaultString(command.serviceName(), "vspicy-admin"));
            ps.setString(3, trimToNull(command.moduleName()));
            ps.setString(4, normalizeAction(command.actionType()));
            ps.setString(5, operationName);
            ps.setString(6, trimToNull(command.requestMethod()));
            ps.setString(7, trimToNull(command.requestUri()));
            ps.setString(8, trimToNull(command.requestParams()));
            ps.setString(9, trimToNull(command.requestBody()));
            setInteger(ps, 10, command.responseStatus());
            ps.setInt(11, command.success() == null || command.success() ? 1 : 0);
            ps.setString(12, trimToNull(command.errorMessage()));
            ps.setString(13, normalizeRisk(command.riskLevel()));
            setLong(ps, 14, command.operatorId());
            ps.setString(15, trimToNull(command.operatorName()));
            ps.setString(16, trimToNull(command.operatorIp()));
            ps.setString(17, trimToNull(command.userAgent()));
            setLong(ps, 18, command.costMs());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException("审计记录创建失败");
        }
        return get(key.longValue());
    }

    @Transactional
    public OperationAuditView record(OperationAuditCreateCommand command) {
        return create(command);
    }

    @Transactional
    public OperationAuditView review(Long id, String remark, Long operatorId) {
        updateHandleStatus(id, "REVIEWED", remark, operatorId);
        return get(id);
    }

    @Transactional
    public OperationAuditView ignore(Long id, String remark, Long operatorId) {
        updateHandleStatus(id, "IGNORED", remark, operatorId);
        return get(id);
    }

    @Transactional
    public OperationAuditView reopen(Long id) {
        mustExist(id);
        jdbcTemplate.update("""
                UPDATE sys_operation_audit_log
                SET handle_status = 'OPEN', handle_remark = NULL, handled_by = NULL, handled_at = NULL
                WHERE id = ?
                """, id);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        mustExist(id);
        jdbcTemplate.update("DELETE FROM sys_operation_audit_log WHERE id = ?", id);
    }

    public OperationAuditCleanupPreviewView cleanupPreview(OperationAuditCleanupCommand command) {
        int beforeDays = safeBeforeDays(command == null ? null : command.beforeDays());
        boolean onlyHandled = command != null && Boolean.TRUE.equals(command.onlyHandled());
        LocalDateTime before = LocalDateTime.now().minusDays(beforeDays);
        List<Object> params = new ArrayList<>();
        String where = cleanupWhere(onlyHandled, params, before);
        long matched = count("SELECT COUNT(*) FROM sys_operation_audit_log " + where, params);
        return new OperationAuditCleanupPreviewView(beforeDays, onlyHandled, DATE_TIME.format(before), matched);
    }

    @Transactional
    public long cleanup(OperationAuditCleanupCommand command) {
        int beforeDays = safeBeforeDays(command == null ? null : command.beforeDays());
        boolean onlyHandled = command == null || command.onlyHandled() == null || Boolean.TRUE.equals(command.onlyHandled());
        LocalDateTime before = LocalDateTime.now().minusDays(beforeDays);
        List<Object> params = new ArrayList<>();
        String where = cleanupWhere(onlyHandled, params, before);
        return jdbcTemplate.update("DELETE FROM sys_operation_audit_log " + where, params.toArray());
    }

    private void updateHandleStatus(Long id, String status, String remark, Long operatorId) {
        mustExist(id);
        jdbcTemplate.update("""
                UPDATE sys_operation_audit_log
                SET handle_status = ?, handle_remark = ?, handled_by = ?, handled_at = NOW()
                WHERE id = ?
                """, normalizeHandleStatus(status), trimToNull(remark), operatorId, id);
    }

    private void mustExist(Long id) {
        if (id == null) {
            throw new BizException("审计日志ID不能为空");
        }
        long count = count("SELECT COUNT(*) FROM sys_operation_audit_log WHERE id = ?", List.of(id));
        if (count == 0) {
            throw new BizException("审计日志不存在");
        }
    }

    private List<OperationAuditMetricItem> distribution(String column) {
        return jdbcTemplate.query("""
                SELECT """ + column + " AS name, COUNT(*) AS value FROM sys_operation_audit_log GROUP BY " + column + " ORDER BY value DESC LIMIT 12",
                (rs, rowNum) -> new OperationAuditMetricItem(defaultString(rs.getString("name"), "UNKNOWN"), rs.getLong("value"))
        );
    }

    private OperationAuditView mapView(ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new OperationAuditView(
                rs.getLong("id"),
                rs.getString("trace_id"),
                rs.getString("service_name"),
                rs.getString("module_name"),
                rs.getString("action_type"),
                rs.getString("operation_name"),
                rs.getString("request_method"),
                rs.getString("request_uri"),
                rs.getString("request_params"),
                rs.getString("request_body"),
                getInteger(rs, "response_status"),
                rs.getInt("success") == 1,
                rs.getString("error_message"),
                rs.getString("risk_level"),
                rs.getString("handle_status"),
                rs.getString("handle_remark"),
                getLong(rs, "operator_id"),
                rs.getString("operator_name"),
                rs.getString("operator_ip"),
                rs.getString("user_agent"),
                getLong(rs, "cost_ms"),
                getLong(rs, "handled_by"),
                format(rs.getTimestamp("handled_at")),
                format(rs.getTimestamp("created_at"))
        );
    }

    private String cleanupWhere(boolean onlyHandled, List<Object> params, LocalDateTime before) {
        params.add(Timestamp.valueOf(before));
        String where = " WHERE created_at < ? ";
        if (onlyHandled) {
            where += " AND handle_status IN ('REVIEWED', 'IGNORED') ";
        }
        return where;
    }

    private int safeBeforeDays(Integer beforeDays) {
        if (beforeDays == null || beforeDays < 7) {
            return 30;
        }
        if (beforeDays > 3650) {
            return 3650;
        }
        return beforeDays;
    }

    private long count(String sql, List<Object> params) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return value == null ? 0 : value;
    }

    private String normalizeAction(String value) {
        String normalized = normalize(value, "OTHER");
        return ACTION_TYPES.contains(normalized) ? normalized : "OTHER";
    }

    private String normalizeRisk(String value) {
        String normalized = normalize(value, "LOW");
        return RISK_LEVELS.contains(normalized) ? normalized : "LOW";
    }

    private String normalizeHandleStatus(String value) {
        String normalized = normalize(value, "OPEN");
        return HANDLE_STATUSES.contains(normalized) ? normalized : "OPEN";
    }

    private String normalize(String value, String defaultValue) {
        if (!hasText(value)) {
            return defaultValue;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 5000 ? trimmed.substring(0, 5000) : trimmed;
    }

    private String defaultString(String value, String defaultValue) {
        return hasText(value) ? value.trim() : defaultValue;
    }

    private String format(Timestamp timestamp) {
        return timestamp == null ? null : DATE_TIME.format(timestamp.toLocalDateTime());
    }

    private Long getLong(ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer getInteger(ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private void setLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setLong(index, value);
        }
    }

    private void setInteger(PreparedStatement ps, int index, Integer value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setInt(index, value);
        }
    }
}
