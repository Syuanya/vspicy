package com.vspicy.admin.service;

import com.vspicy.admin.dto.SystemConfigBatchUpdateCommand;
import com.vspicy.admin.dto.SystemConfigBatchUpdateItem;
import com.vspicy.admin.dto.SystemConfigChangeLogView;
import com.vspicy.admin.dto.SystemConfigGroupView;
import com.vspicy.admin.dto.SystemConfigMetricItem;
import com.vspicy.admin.dto.SystemConfigOverviewView;
import com.vspicy.admin.dto.SystemConfigSaveCommand;
import com.vspicy.admin.dto.SystemConfigView;
import com.vspicy.common.exception.BizException;
import org.springframework.dao.DuplicateKeyException;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SystemConfigService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> CATEGORIES = Set.of("SYSTEM", "UPLOAD", "VIDEO", "NOTIFICATION", "SECURITY", "BILLING", "OTHER");
    private static final Set<String> VALUE_TYPES = Set.of("STRING", "NUMBER", "BOOLEAN", "JSON", "TEXT");
    private static final Set<String> STATUSES = Set.of("ENABLED", "DISABLED");

    private final JdbcTemplate jdbcTemplate;

    public SystemConfigService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SystemConfigGroupView> groups() {
        return jdbcTemplate.query("""
                SELECT category AS group_code,
                       COUNT(*) AS total_count,
                       SUM(CASE WHEN status = 'ENABLED' THEN 1 ELSE 0 END) AS enabled_count,
                       SUM(CASE WHEN status = 'DISABLED' THEN 1 ELSE 0 END) AS disabled_count
                FROM sys_system_config
                GROUP BY category
                ORDER BY category ASC
                """, (rs, rowNum) -> new SystemConfigGroupView(
                rs.getString("group_code"),
                rs.getLong("total_count"),
                rs.getLong("enabled_count"),
                rs.getLong("disabled_count")
        ));
    }


    public SystemConfigOverviewView overview() {
        long total = count("SELECT COUNT(*) FROM sys_system_config", List.of());
        long enabled = count("SELECT COUNT(*) FROM sys_system_config WHERE status = 'ENABLED'", List.of());
        long disabled = count("SELECT COUNT(*) FROM sys_system_config WHERE status = 'DISABLED'", List.of());
        long editable = count("SELECT COUNT(*) FROM sys_system_config WHERE editable = 1", List.of());
        long sensitive = count("SELECT COUNT(*) FROM sys_system_config WHERE is_sensitive = 1", List.of());
        long changedToday = count("SELECT COUNT(*) FROM sys_system_config_change_log WHERE DATE(created_at) = CURDATE()", List.of());
        return new SystemConfigOverviewView(
                total,
                enabled,
                disabled,
                editable,
                sensitive,
                changedToday,
                distribution("category"),
                distribution("value_type"),
                distribution("status")
        );
    }

    /**
     * 兼容旧版系统配置列表调用。
     *
     * <p>旧版 controller 曾按 category、keyword、page、size 调用 list。新版列表已经
     * 扩展筛选参数，保留该重载用于避免覆盖式部署时残留旧文件导致编译失败。
     */
    public List<SystemConfigView> list(String category, String keyword, Integer page, Integer size) {
        int safeSize = size == null || size <= 0 ? 100 : Math.min(size, 1000);
        return list(category, null, null, null, null, keyword, false, safeSize);
    }

    public List<SystemConfigView> list(
            String category,
            String valueType,
            String status,
            Boolean editable,
            Boolean sensitive,
            String keyword,
            Boolean includeSensitive,
            Integer limit
    ) {
        int safeLimit = limit == null || limit <= 0 || limit > 1000 ? 200 : limit;
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");

        if (hasText(category)) {
            where.append(" AND category = ? ");
            params.add(normalizeCategory(category));
        }
        if (hasText(valueType)) {
            where.append(" AND value_type = ? ");
            params.add(normalizeValueType(valueType));
        }
        if (hasText(status)) {
            where.append(" AND status = ? ");
            params.add(normalizeStatus(status));
        }
        if (editable != null) {
            where.append(" AND editable = ? ");
            params.add(editable ? 1 : 0);
        }
        if (sensitive != null) {
            where.append(" AND is_sensitive = ? ");
            params.add(sensitive ? 1 : 0);
        }
        if (hasText(keyword)) {
            where.append("""
                    AND (
                      config_key LIKE ?
                      OR config_name LIKE ?
                      OR description LIKE ?
                      OR category LIKE ?
                    )
                    """);
            String like = "%" + keyword.trim() + "%";
            for (int i = 0; i < 4; i++) {
                params.add(like);
            }
        }
        params.add(safeLimit);

        return jdbcTemplate.query("""
                SELECT id, config_key, config_name, config_value, default_value, category,
                       value_type, editable, is_sensitive, is_required, validation_rule, description,
                       status, version, updated_by, created_at, updated_at
                FROM sys_system_config
                """ + where + " ORDER BY category ASC, config_key ASC LIMIT ?", (rs, rowNum) -> mapView(rs, includeSensitive), params.toArray());
    }

    public SystemConfigView get(Long id, Boolean includeSensitive) {
        if (id == null) {
            throw new BizException("配置ID不能为空");
        }
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, config_key, config_name, config_value, default_value, category,
                           value_type, editable, is_sensitive, is_required, validation_rule, description,
                           status, version, updated_by, created_at, updated_at
                    FROM sys_system_config
                    WHERE id = ?
                    """, (rs, rowNum) -> mapView(rs, includeSensitive), id);
        } catch (EmptyResultDataAccessException ignored) {
            throw new BizException("系统配置不存在");
        }
    }

    public SystemConfigView getByKey(String configKey, Boolean includeSensitive) {
        String key = trimToNull(configKey);
        if (key == null) {
            throw new BizException("配置键不能为空");
        }
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, config_key, config_name, config_value, default_value, category,
                           value_type, editable, is_sensitive, is_required, validation_rule, description,
                           status, version, updated_by, created_at, updated_at
                    FROM sys_system_config
                    WHERE config_key = ?
                    """, (rs, rowNum) -> mapView(rs, includeSensitive), key);
        } catch (EmptyResultDataAccessException ignored) {
            throw new BizException("系统配置不存在：" + key);
        }
    }

    @Transactional
    public SystemConfigView create(SystemConfigSaveCommand command, Long operatorId, String operatorName, String operatorIp) {
        if (command == null) {
            throw new BizException("配置内容不能为空");
        }
        String key = validateKey(command.configKey());
        String name = required(command.configName(), "配置名称不能为空");
        String type = normalizeValueType(command.valueType());
        String category = normalizeCategory(command.category());
        String status = normalizeStatus(defaultString(command.status(), "ENABLED"));
        String value = trimValue(command.configValue());
        validateValue(type, Boolean.TRUE.equals(command.required()), value);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO sys_system_config(
                          config_key, config_name, config_value, default_value, category, value_type,
                          editable, is_sensitive, is_required, validation_rule, description, status, updated_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, key);
                ps.setString(2, name);
                ps.setString(3, value);
                ps.setString(4, trimValue(command.defaultValue()));
                ps.setString(5, category);
                ps.setString(6, type);
                ps.setInt(7, bool(command.editable(), true));
                ps.setInt(8, bool(command.sensitive(), false));
                ps.setInt(9, bool(command.required(), false));
                ps.setString(10, trimToNull(command.validationRule()));
                ps.setString(11, trimToNull(command.description()));
                ps.setString(12, status);
                setLong(ps, 13, operatorId);
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException ignored) {
            throw new BizException("配置键已存在：" + key);
        }
        Number generated = keyHolder.getKey();
        if (generated == null) {
            throw new BizException("系统配置创建失败");
        }
        Long id = generated.longValue();
        recordChange(id, key, null, value, "CREATE", command.changeReason(), operatorId, operatorName, operatorIp);
        return get(id, true);
    }

    @Transactional
    public SystemConfigView update(Long id, SystemConfigSaveCommand command, Long operatorId, String operatorName, String operatorIp) {
        SystemConfigView old = get(id, true);
        if (!Boolean.TRUE.equals(old.editable())) {
            throw new BizException("该配置不允许后台编辑：" + old.configKey());
        }
        if (command == null) {
            throw new BizException("配置内容不能为空");
        }
        String name = hasText(command.configName()) ? command.configName().trim() : old.configName();
        String type = hasText(command.valueType()) ? normalizeValueType(command.valueType()) : old.valueType();
        String category = hasText(command.category()) ? normalizeCategory(command.category()) : old.category();
        String status = hasText(command.status()) ? normalizeStatus(command.status()) : old.status();
        String value = command.configValue() == null ? old.rawConfigValue() : trimValue(command.configValue());
        boolean required = command.required() == null ? Boolean.TRUE.equals(old.required()) : command.required();
        validateValue(type, required, value);

        jdbcTemplate.update("""
                UPDATE sys_system_config
                SET config_name = ?, config_value = ?, default_value = ?, category = ?, value_type = ?,
                    editable = ?, is_sensitive = ?, is_required = ?, validation_rule = ?, description = ?,
                    status = ?, version = version + 1, updated_by = ?
                WHERE id = ?
                """,
                name,
                value,
                command.defaultValue() == null ? old.defaultValue() : trimValue(command.defaultValue()),
                category,
                type,
                bool(command.editable(), Boolean.TRUE.equals(old.editable())) ,
                bool(command.sensitive(), Boolean.TRUE.equals(old.sensitive())),
                required ? 1 : 0,
                command.validationRule() == null ? old.validationRule() : trimToNull(command.validationRule()),
                command.description() == null ? old.description() : trimToNull(command.description()),
                status,
                operatorId,
                id);
        if (!equalsText(old.rawConfigValue(), value)) {
            recordChange(id, old.configKey(), old.rawConfigValue(), value, "UPDATE", command.changeReason(), operatorId, operatorName, operatorIp);
        }
        return get(id, true);
    }

    @Transactional
    public List<SystemConfigView> batchUpdate(SystemConfigBatchUpdateCommand command, Long operatorId, String operatorName, String operatorIp) {
        if (command == null || command.items() == null || command.items().isEmpty()) {
            throw new BizException("批量更新项不能为空");
        }
        if (command.items().size() > 100) {
            throw new BizException("单次最多批量更新100个配置");
        }
        List<SystemConfigView> result = new ArrayList<>();
        for (SystemConfigBatchUpdateItem item : command.items()) {
            if (item == null) {
                continue;
            }
            SystemConfigView old = getByKey(item.configKey(), true);
            SystemConfigSaveCommand update = new SystemConfigSaveCommand(
                    old.configKey(), old.configName(), item.configValue(), old.defaultValue(), old.category(), old.valueType(),
                    old.editable(), old.sensitive(), old.required(), old.validationRule(), old.description(), old.status(), command.changeReason()
            );
            result.add(update(old.id(), update, operatorId, operatorName, operatorIp));
        }
        return result;
    }

    @Transactional
    public SystemConfigView changeStatus(Long id, String status, String reason, Long operatorId, String operatorName, String operatorIp) {
        SystemConfigView old = get(id, true);
        String next = normalizeStatus(status);
        if (next.equals(old.status())) {
            return old;
        }
        jdbcTemplate.update("UPDATE sys_system_config SET status = ?, version = version + 1, updated_by = ? WHERE id = ?", next, operatorId, id);
        recordChange(id, old.configKey(), old.status(), next, next.equals("ENABLED") ? "ENABLE" : "DISABLE", reason, operatorId, operatorName, operatorIp);
        return get(id, true);
    }

    @Transactional
    public SystemConfigView resetToDefault(Long id, String reason, Long operatorId, String operatorName, String operatorIp) {
        SystemConfigView old = get(id, true);
        if (!Boolean.TRUE.equals(old.editable())) {
            throw new BizException("该配置不允许后台编辑：" + old.configKey());
        }
        String defaultValue = trimValue(old.defaultValue());
        validateValue(old.valueType(), Boolean.TRUE.equals(old.required()), defaultValue);
        jdbcTemplate.update("UPDATE sys_system_config SET config_value = ?, version = version + 1, updated_by = ? WHERE id = ?", defaultValue, operatorId, id);
        recordChange(id, old.configKey(), old.rawConfigValue(), defaultValue, "RESET", reason, operatorId, operatorName, operatorIp);
        return get(id, true);
    }

    public List<SystemConfigChangeLogView> changes(Long id, Integer limit) {
        if (id == null) {
            throw new BizException("配置ID不能为空");
        }
        mustExist(id);
        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 100 : limit;
        return jdbcTemplate.query("""
                SELECT id, config_id, config_key, before_value, after_value, change_type, change_reason,
                       operator_id, operator_name, operator_ip, created_at
                FROM sys_system_config_change_log
                WHERE config_id = ?
                ORDER BY id DESC
                LIMIT ?
                """, this::mapChangeLog, id, safeLimit);
    }

    @Transactional
    public void delete(Long id, String reason, Long operatorId, String operatorName, String operatorIp) {
        SystemConfigView old = get(id, true);
        if (Boolean.TRUE.equals(old.sensitive())) {
            throw new BizException("敏感配置不允许直接删除，请先禁用或通过脚本处理：" + old.configKey());
        }
        recordChange(id, old.configKey(), old.rawConfigValue(), null, "DELETE", reason, operatorId, operatorName, operatorIp);
        jdbcTemplate.update("DELETE FROM sys_system_config WHERE id = ?", id);
    }

    private void mustExist(Long id) {
        if (count("SELECT COUNT(*) FROM sys_system_config WHERE id = ?", List.of(id)) == 0) {
            throw new BizException("系统配置不存在");
        }
    }

    private void recordChange(Long configId, String configKey, String beforeValue, String afterValue, String changeType, String reason, Long operatorId, String operatorName, String operatorIp) {
        jdbcTemplate.update("""
                INSERT INTO sys_system_config_change_log(
                  config_id, config_key, before_value, after_value, change_type, change_reason,
                  operator_id, operator_name, operator_ip
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, configId, configKey, beforeValue, afterValue, changeType, trimToNull(reason), operatorId, trimToNull(operatorName), trimToNull(operatorIp));
    }

    private SystemConfigView mapView(ResultSet rs, Boolean includeSensitive) throws java.sql.SQLException {
        boolean sensitive = rs.getInt("is_sensitive") == 1;
        String raw = rs.getString("config_value");
        boolean reveal = Boolean.TRUE.equals(includeSensitive);
        String display = sensitive && !reveal ? mask(raw) : raw;
        return new SystemConfigView(
                rs.getLong("id"),
                rs.getString("config_key"),
                rs.getString("config_name"),
                display,
                reveal ? raw : null,
                rs.getString("default_value"),
                rs.getString("category"),
                rs.getString("value_type"),
                rs.getInt("editable") == 1,
                sensitive,
                rs.getInt("is_required") == 1,
                rs.getString("validation_rule"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getInt("version"),
                getNullableLong(rs, "updated_by"),
                format(rs.getTimestamp("created_at")),
                format(rs.getTimestamp("updated_at"))
        );
    }

    private SystemConfigChangeLogView mapChangeLog(ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SystemConfigChangeLogView(
                rs.getLong("id"),
                rs.getLong("config_id"),
                rs.getString("config_key"),
                rs.getString("before_value"),
                rs.getString("after_value"),
                rs.getString("change_type"),
                rs.getString("change_reason"),
                getNullableLong(rs, "operator_id"),
                rs.getString("operator_name"),
                rs.getString("operator_ip"),
                format(rs.getTimestamp("created_at"))
        );
    }

    private List<SystemConfigMetricItem> distribution(String column) {
        String safeColumn = switch (column) {
            case "category", "value_type", "status" -> column;
            default -> throw new IllegalArgumentException("unsupported distribution column");
        };
        return jdbcTemplate.query("SELECT " + safeColumn + " AS name, COUNT(*) AS count FROM sys_system_config GROUP BY " + safeColumn + " ORDER BY count DESC",
                (rs, rowNum) -> new SystemConfigMetricItem(rs.getString("name"), rs.getLong("count")));
    }

    private long count(String sql, List<Object> params) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return value == null ? 0L : value;
    }

    private String validateKey(String value) {
        String key = required(value, "配置键不能为空");
        if (!key.matches("^[a-zA-Z][a-zA-Z0-9_.:-]{1,159}$")) {
            throw new BizException("配置键格式不合法，只允许字母、数字、下划线、点、冒号和横线，且必须以字母开头");
        }
        return key;
    }

    private void validateValue(String type, boolean required, String value) {
        if (required && (value == null || value.isBlank())) {
            throw new BizException("必填配置值不能为空");
        }
        if (value == null || value.isBlank()) {
            return;
        }
        switch (normalizeValueType(type)) {
            case "NUMBER" -> {
                try {
                    Double.parseDouble(value.trim());
                } catch (NumberFormatException ignored) {
                    throw new BizException("NUMBER 类型配置必须是数字");
                }
            }
            case "BOOLEAN" -> {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (!normalized.equals("true") && !normalized.equals("false")) {
                    throw new BizException("BOOLEAN 类型配置必须是 true 或 false");
                }
            }
            case "JSON" -> {
                String trimmed = value.trim();
                if (!(trimmed.startsWith("{") && trimmed.endsWith("}")) && !(trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                    throw new BizException("JSON 类型配置必须以 { } 或 [ ] 包裹");
                }
            }
            default -> {
                // STRING/TEXT 不做强约束。
            }
        }
    }

    private String normalizeCategory(String value) {
        String normalized = defaultString(value, "SYSTEM").trim().toUpperCase(Locale.ROOT);
        return CATEGORIES.contains(normalized) ? normalized : "OTHER";
    }

    private String normalizeValueType(String value) {
        String normalized = defaultString(value, "STRING").trim().toUpperCase(Locale.ROOT);
        if (!VALUE_TYPES.contains(normalized)) {
            throw new BizException("不支持的配置类型：" + value);
        }
        return normalized;
    }

    private String normalizeStatus(String value) {
        String normalized = defaultString(value, "ENABLED").trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new BizException("不支持的配置状态：" + value);
        }
        return normalized;
    }

    private String required(String value, String message) {
        String text = trimToNull(value);
        if (text == null) {
            throw new BizException(message);
        }
        return text;
    }

    private int bool(Boolean value, boolean defaultValue) {
        return (value == null ? defaultValue : value) ? 1 : 0;
    }

    private String trimValue(String value) {
        return value == null ? null : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean equalsText(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        return a.equals(b);
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "******";
        }
        if (value.length() <= 4) {
            return "******";
        }
        return value.substring(0, 2) + "******" + value.substring(value.length() - 2);
    }

    private Long getNullableLong(ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private void setLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setLong(index, value);
        }
    }

    private String format(Timestamp timestamp) {
        return timestamp == null ? null : DATE_TIME.format(timestamp.toLocalDateTime());
    }
}
