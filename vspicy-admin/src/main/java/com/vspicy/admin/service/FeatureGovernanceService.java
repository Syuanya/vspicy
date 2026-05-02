package com.vspicy.admin.service;

import com.vspicy.admin.dto.FeatureCheckIssueView;
import com.vspicy.admin.dto.FeatureCheckRunView;
import com.vspicy.admin.dto.FeatureGovernanceOverviewView;
import com.vspicy.admin.dto.FeatureMetricItem;
import com.vspicy.admin.dto.FeatureRegistryCommand;
import com.vspicy.admin.dto.FeatureRegistryView;
import com.vspicy.common.exception.BizException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FeatureGovernanceService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> FEATURE_TYPES = Set.of("PAGE", "API", "BUTTON", "JOB", "INTEGRATION");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "NORMAL", "HIGH", "CRITICAL");
    private static final Set<String> STATUSES = Set.of("ENABLED", "DISABLED");

    private final JdbcTemplate jdbcTemplate;

    public FeatureGovernanceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FeatureGovernanceOverviewView overview() {
        long total = count("SELECT COUNT(*) FROM sys_feature_registry WHERE deleted = 0");
        long enabled = count("SELECT COUNT(*) FROM sys_feature_registry WHERE deleted = 0 AND status = 'ENABLED'");
        long disabled = count("SELECT COUNT(*) FROM sys_feature_registry WHERE deleted = 0 AND status = 'DISABLED'");
        long open = count("SELECT COUNT(*) FROM sys_feature_check_issue WHERE deleted = 0 AND status = 'OPEN'");
        long blocker = count("SELECT COUNT(*) FROM sys_feature_check_issue WHERE deleted = 0 AND status = 'OPEN' AND severity = 'BLOCKER'");
        long high = count("SELECT COUNT(*) FROM sys_feature_check_issue WHERE deleted = 0 AND status = 'OPEN' AND severity = 'HIGH'");
        long warn = count("SELECT COUNT(*) FROM sys_feature_check_issue WHERE deleted = 0 AND status = 'OPEN' AND severity = 'WARN'");
        long resolved = count("SELECT COUNT(*) FROM sys_feature_check_issue WHERE deleted = 0 AND status = 'RESOLVED'");

        FeatureCheckRunView lastRun = latestRun();
        return new FeatureGovernanceOverviewView(
                total,
                enabled,
                disabled,
                open,
                blocker,
                high,
                warn,
                resolved,
                lastRun == null ? null : lastRun.runNo(),
                lastRun == null ? null : lastRun.createdAt(),
                metric("SELECT feature_type name, COUNT(*) value FROM sys_feature_registry WHERE deleted = 0 GROUP BY feature_type ORDER BY value DESC"),
                metric("SELECT module_name name, COUNT(*) value FROM sys_feature_registry WHERE deleted = 0 GROUP BY module_name ORDER BY value DESC"),
                metric("SELECT issue_type name, COUNT(*) value FROM sys_feature_check_issue WHERE deleted = 0 AND status = 'OPEN' GROUP BY issue_type ORDER BY value DESC")
        );
    }

    public List<FeatureRegistryView> listFeatures(
            String moduleName,
            String featureType,
            String status,
            String riskLevel,
            String keyword,
            Integer limit
    ) {
        int safeLimit = safeLimit(limit);
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE deleted = 0 ");
        if (hasText(moduleName)) {
            where.append(" AND module_name = ? ");
            params.add(moduleName.trim().toUpperCase(Locale.ROOT));
        }
        if (hasText(featureType)) {
            where.append(" AND feature_type = ? ");
            params.add(normalizeFeatureType(featureType));
        }
        if (hasText(status)) {
            where.append(" AND status = ? ");
            params.add(normalizeStatus(status));
        }
        if (hasText(riskLevel)) {
            where.append(" AND risk_level = ? ");
            params.add(normalizeRiskLevel(riskLevel));
        }
        if (hasText(keyword)) {
            where.append("""
                    AND (
                      feature_code LIKE ?
                      OR feature_name LIKE ?
                      OR route_path LIKE ?
                      OR api_path LIKE ?
                      OR permission_code LIKE ?
                      OR menu_title LIKE ?
                      OR description LIKE ?
                    )
                    """);
            String value = "%" + keyword.trim() + "%";
            for (int i = 0; i < 7; i++) {
                params.add(value);
            }
        }
        params.add(safeLimit);
        return jdbcTemplate.query("""
                SELECT *
                FROM sys_feature_registry
                """ + where + """
                ORDER BY
                  CASE risk_level WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'NORMAL' THEN 3 ELSE 4 END,
                  module_name ASC,
                  feature_type ASC,
                  id DESC
                LIMIT ?
                """, (rs, rowNum) -> toFeatureView(rs), params.toArray());
    }

    public FeatureRegistryView getFeature(Long id) {
        return mustGetFeature(id);
    }

    @Transactional
    public FeatureRegistryView createFeature(FeatureRegistryCommand command, Long operatorId) {
        validateFeature(command, true);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO sys_feature_registry(
                      feature_code, feature_name, feature_type, module_name, service_name,
                      route_path, api_path, api_method, permission_code,
                      menu_title, menu_group, owner, risk_level, status,
                      source_type, description, created_by, updated_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MANUAL', ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, command.featureCode().trim());
            ps.setString(2, command.featureName().trim());
            ps.setString(3, normalizeFeatureType(command.featureType()));
            ps.setString(4, defaultString(command.moduleName(), "SYSTEM").toUpperCase(Locale.ROOT));
            ps.setString(5, defaultString(command.serviceName(), "vspicy-admin"));
            ps.setString(6, trimToNull(command.routePath()));
            ps.setString(7, trimToNull(command.apiPath()));
            ps.setString(8, normalizeApiMethod(command.apiMethod()));
            ps.setString(9, trimToNull(command.permissionCode()));
            ps.setString(10, trimToNull(command.menuTitle()));
            ps.setString(11, trimToNull(command.menuGroup()));
            ps.setString(12, trimToNull(command.owner()));
            ps.setString(13, normalizeRiskLevel(command.riskLevel()));
            ps.setString(14, normalizeStatus(defaultString(command.status(), "ENABLED")));
            ps.setString(15, trimToNull(command.description()));
            setLong(ps, 16, operatorId);
            setLong(ps, 17, operatorId);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException("功能登记失败");
        }
        return getFeature(key.longValue());
    }

    @Transactional
    public FeatureRegistryView updateFeature(Long id, FeatureRegistryCommand command, Long operatorId) {
        mustGetFeature(id);
        validateFeature(command, false);
        jdbcTemplate.update("""
                UPDATE sys_feature_registry
                SET feature_name = ?,
                    feature_type = ?,
                    module_name = ?,
                    service_name = ?,
                    route_path = ?,
                    api_path = ?,
                    api_method = ?,
                    permission_code = ?,
                    menu_title = ?,
                    menu_group = ?,
                    owner = ?,
                    risk_level = ?,
                    status = ?,
                    description = ?,
                    updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                command.featureName().trim(),
                normalizeFeatureType(command.featureType()),
                defaultString(command.moduleName(), "SYSTEM").toUpperCase(Locale.ROOT),
                defaultString(command.serviceName(), "vspicy-admin"),
                trimToNull(command.routePath()),
                trimToNull(command.apiPath()),
                normalizeApiMethod(command.apiMethod()),
                trimToNull(command.permissionCode()),
                trimToNull(command.menuTitle()),
                trimToNull(command.menuGroup()),
                trimToNull(command.owner()),
                normalizeRiskLevel(command.riskLevel()),
                normalizeStatus(defaultString(command.status(), "ENABLED")),
                trimToNull(command.description()),
                operatorId,
                id);
        return getFeature(id);
    }

    @Transactional
    public FeatureRegistryView enableFeature(Long id, Long operatorId) {
        updateFeatureStatus(id, "ENABLED", operatorId);
        return getFeature(id);
    }

    @Transactional
    public FeatureRegistryView disableFeature(Long id, Long operatorId) {
        updateFeatureStatus(id, "DISABLED", operatorId);
        return getFeature(id);
    }

    @Transactional
    public void deleteFeature(Long id, Long operatorId) {
        mustGetFeature(id);
        jdbcTemplate.update("UPDATE sys_feature_registry SET deleted = 1, updated_by = ? WHERE id = ?", operatorId, id);
    }

    @Transactional
    public FeatureCheckRunView runCheck(Long operatorId) {
        List<FeatureRegistryView> features = listFeatures(null, null, "ENABLED", null, null, 5000);
        String runNo = "FG" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + UUID.randomUUID().toString().substring(0, 8);

        jdbcTemplate.update("""
                UPDATE sys_feature_check_issue
                SET status = 'RESOLVED', handle_remark = '新一轮检查已重新生成', handled_by = ?, handled_at = NOW()
                WHERE deleted = 0 AND status = 'OPEN'
                """, operatorId);

        List<IssueDraft> issues = new ArrayList<>();
        addDuplicateIssues(features, runNo, issues, "routePath", FeatureRegistryView::routePath, "DUPLICATE_ROUTE", "BLOCKER", "ROUTE", "路由路径重复，会导致后台点击进入错误页面或路由覆盖");
        addDuplicateIssues(features, runNo, issues, "permissionCode", FeatureRegistryView::permissionCode, "DUPLICATE_PERMISSION", "HIGH", "PERMISSION", "权限码重复，会导致按钮/接口授权边界不清晰");
        addDuplicateApiIssues(features, runNo, issues);
        addDuplicateMenuIssues(features, runNo, issues);

        for (FeatureRegistryView feature : features) {
            if ("PAGE".equalsIgnoreCase(feature.featureType()) && !hasText(feature.routePath())) {
                issues.add(issue(runNo, "MISSING_ROUTE", "BLOCKER", feature, "ROUTE", "", "页面功能缺少 routePath，前端无法稳定跳转"));
            }
            if (("PAGE".equalsIgnoreCase(feature.featureType()) || "API".equalsIgnoreCase(feature.featureType()) || "BUTTON".equalsIgnoreCase(feature.featureType()))
                    && !hasText(feature.permissionCode())) {
                issues.add(issue(runNo, "MISSING_PERMISSION", "HIGH", feature, "PERMISSION", "", "页面/API/按钮缺少 permissionCode，网关和按钮权限无法统一控制"));
            }
            if ("API".equalsIgnoreCase(feature.featureType()) && !hasText(feature.apiPath())) {
                issues.add(issue(runNo, "MISSING_API_PATH", "WARN", feature, "API", "", "API 功能缺少 apiPath，无法纳入接口治理"));
            }
        }

        for (IssueDraft draft : issues) {
            insertIssue(draft);
        }

        int duplicateRoutes = countIssues(issues, "DUPLICATE_ROUTE");
        int duplicatePermissions = countIssues(issues, "DUPLICATE_PERMISSION");
        int duplicateApis = countIssues(issues, "DUPLICATE_API");
        int missingRoutes = countIssues(issues, "MISSING_ROUTE");
        int missingPermissions = countIssues(issues, "MISSING_PERMISSION");

        jdbcTemplate.update("""
                INSERT INTO sys_feature_check_run(
                  run_no, total_features, total_issues, open_issues,
                  duplicate_routes, duplicate_permissions, duplicate_apis,
                  missing_routes, missing_permissions, run_status, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', ?)
                """, runNo, features.size(), issues.size(), issues.size(), duplicateRoutes, duplicatePermissions, duplicateApis, missingRoutes, missingPermissions, operatorId);

        return latestRun();
    }

    public List<FeatureCheckIssueView> listIssues(String status, String severity, String issueType, String keyword, Integer limit) {
        int safeLimit = safeLimit(limit);
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE deleted = 0 ");
        if (hasText(status)) {
            where.append(" AND status = ? ");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (hasText(severity)) {
            where.append(" AND severity = ? ");
            params.add(severity.trim().toUpperCase(Locale.ROOT));
        }
        if (hasText(issueType)) {
            where.append(" AND issue_type = ? ");
            params.add(issueType.trim().toUpperCase(Locale.ROOT));
        }
        if (hasText(keyword)) {
            where.append("""
                    AND (
                      feature_code LIKE ?
                      OR feature_name LIKE ?
                      OR module_name LIKE ?
                      OR target_value LIKE ?
                      OR issue_message LIKE ?
                    )
                    """);
            String value = "%" + keyword.trim() + "%";
            for (int i = 0; i < 5; i++) {
                params.add(value);
            }
        }
        params.add(safeLimit);
        return jdbcTemplate.query("""
                SELECT *
                FROM sys_feature_check_issue
                """ + where + """
                ORDER BY
                  CASE severity WHEN 'BLOCKER' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'WARN' THEN 3 ELSE 4 END,
                  id DESC
                LIMIT ?
                """, (rs, rowNum) -> toIssueView(rs), params.toArray());
    }

    @Transactional
    public FeatureCheckIssueView resolveIssue(Long id, String remark, Long operatorId) {
        updateIssueStatus(id, "RESOLVED", remark, operatorId);
        return getIssue(id);
    }

    @Transactional
    public FeatureCheckIssueView ignoreIssue(Long id, String remark, Long operatorId) {
        updateIssueStatus(id, "IGNORED", remark, operatorId);
        return getIssue(id);
    }

    @Transactional
    public FeatureCheckIssueView reopenIssue(Long id) {
        jdbcTemplate.update("""
                UPDATE sys_feature_check_issue
                SET status = 'OPEN', handle_remark = NULL, handled_by = NULL, handled_at = NULL
                WHERE id = ? AND deleted = 0
                """, id);
        return getIssue(id);
    }

    private void addDuplicateIssues(
            List<FeatureRegistryView> features,
            String runNo,
            List<IssueDraft> issues,
            String fieldName,
            Function<FeatureRegistryView, String> getter,
            String issueType,
            String severity,
            String targetType,
            String message
    ) {
        Map<String, List<FeatureRegistryView>> group = features.stream()
                .filter(feature -> hasText(getter.apply(feature)))
                .collect(Collectors.groupingBy(feature -> getter.apply(feature).trim()));
        group.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .forEach(entry -> entry.getValue().forEach(feature -> issues.add(issue(runNo, issueType, severity, feature, targetType, entry.getKey(), message + "：" + fieldName + "=" + entry.getKey()))));
    }

    private void addDuplicateApiIssues(List<FeatureRegistryView> features, String runNo, List<IssueDraft> issues) {
        Map<String, List<FeatureRegistryView>> group = new HashMap<>();
        for (FeatureRegistryView feature : features) {
            if (!hasText(feature.apiPath())) {
                continue;
            }
            String key = defaultString(feature.apiMethod(), "ALL").toUpperCase(Locale.ROOT) + " " + feature.apiPath().trim();
            group.computeIfAbsent(key, ignored -> new ArrayList<>()).add(feature);
        }
        group.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .forEach(entry -> entry.getValue().forEach(feature -> issues.add(issue(runNo, "DUPLICATE_API", "BLOCKER", feature, "API", entry.getKey(), "API 路径和方法重复，可能导致网关权限规则冲突：" + entry.getKey()))));
    }

    private void addDuplicateMenuIssues(List<FeatureRegistryView> features, String runNo, List<IssueDraft> issues) {
        Map<String, List<FeatureRegistryView>> group = new HashMap<>();
        for (FeatureRegistryView feature : features) {
            if (!hasText(feature.menuTitle())) {
                continue;
            }
            String key = defaultString(feature.menuGroup(), "default") + "::" + feature.menuTitle().trim();
            group.computeIfAbsent(key, ignored -> new ArrayList<>()).add(feature);
        }
        group.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .forEach(entry -> entry.getValue().forEach(feature -> issues.add(issue(runNo, "MENU_DUPLICATE", "WARN", feature, "MENU", entry.getKey(), "同一菜单分组下菜单名称重复，容易造成后台入口混淆：" + entry.getKey()))));
    }

    private IssueDraft issue(String runNo, String issueType, String severity, FeatureRegistryView feature, String targetType, String targetValue, String message) {
        return new IssueDraft(runNo, issueType, severity, feature.id(), feature.featureCode(), feature.featureName(), feature.moduleName(), targetType, targetValue, message);
    }

    private void insertIssue(IssueDraft issue) {
        jdbcTemplate.update("""
                INSERT INTO sys_feature_check_issue(
                  run_no, issue_type, severity, feature_id, feature_code, feature_name,
                  module_name, target_type, target_value, issue_message, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN')
                """, issue.runNo, issue.issueType, issue.severity, issue.featureId, issue.featureCode, issue.featureName,
                issue.moduleName, issue.targetType, issue.targetValue, issue.issueMessage);
    }

    private int countIssues(List<IssueDraft> issues, String type) {
        return (int) issues.stream().filter(issue -> type.equals(issue.issueType)).count();
    }

    private FeatureCheckRunView latestRun() {
        List<FeatureCheckRunView> rows = jdbcTemplate.query("""
                SELECT *
                FROM sys_feature_check_run
                ORDER BY id DESC
                LIMIT 1
                """, (rs, rowNum) -> new FeatureCheckRunView(
                rs.getString("run_no"),
                rs.getInt("total_features"),
                rs.getInt("total_issues"),
                rs.getInt("open_issues"),
                rs.getInt("duplicate_routes"),
                rs.getInt("duplicate_permissions"),
                rs.getInt("duplicate_apis"),
                rs.getInt("missing_routes"),
                rs.getInt("missing_permissions"),
                rs.getString("run_status"),
                format(rs.getTimestamp("created_at"))
        ));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<FeatureMetricItem> metric(String sql) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> new FeatureMetricItem(rs.getString("name"), rs.getLong("value")));
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private FeatureRegistryView mustGetFeature(Long id) {
        if (id == null) {
            throw new BizException("功能ID不能为空");
        }
        List<FeatureRegistryView> rows = jdbcTemplate.query("SELECT * FROM sys_feature_registry WHERE id = ? AND deleted = 0", (rs, rowNum) -> toFeatureView(rs), id);
        if (rows.isEmpty()) {
            throw new BizException("功能不存在或已删除");
        }
        return rows.get(0);
    }

    private FeatureCheckIssueView getIssue(Long id) {
        if (id == null) {
            throw new BizException("问题ID不能为空");
        }
        List<FeatureCheckIssueView> rows = jdbcTemplate.query("SELECT * FROM sys_feature_check_issue WHERE id = ? AND deleted = 0", (rs, rowNum) -> toIssueView(rs), id);
        if (rows.isEmpty()) {
            throw new BizException("检查问题不存在或已删除");
        }
        return rows.get(0);
    }

    private void updateFeatureStatus(Long id, String status, Long operatorId) {
        mustGetFeature(id);
        jdbcTemplate.update("UPDATE sys_feature_registry SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0", status, operatorId, id);
    }

    private void updateIssueStatus(Long id, String status, String remark, Long operatorId) {
        getIssue(id);
        jdbcTemplate.update("""
                UPDATE sys_feature_check_issue
                SET status = ?, handle_remark = ?, handled_by = ?, handled_at = NOW()
                WHERE id = ? AND deleted = 0
                """, status, trimToNull(remark), operatorId, id);
    }

    private FeatureRegistryView toFeatureView(ResultSet rs) {
        try {
            return new FeatureRegistryView(
                    rs.getLong("id"),
                    rs.getString("feature_code"),
                    rs.getString("feature_name"),
                    rs.getString("feature_type"),
                    rs.getString("module_name"),
                    rs.getString("service_name"),
                    rs.getString("route_path"),
                    rs.getString("api_path"),
                    rs.getString("api_method"),
                    rs.getString("permission_code"),
                    rs.getString("menu_title"),
                    rs.getString("menu_group"),
                    rs.getString("owner"),
                    rs.getString("risk_level"),
                    rs.getString("status"),
                    rs.getString("source_type"),
                    rs.getString("description"),
                    format(rs.getTimestamp("created_at")),
                    format(rs.getTimestamp("updated_at"))
            );
        } catch (Exception ex) {
            throw new BizException("功能数据解析失败: " + ex.getMessage());
        }
    }

    private FeatureCheckIssueView toIssueView(ResultSet rs) {
        try {
            return new FeatureCheckIssueView(
                    rs.getLong("id"),
                    rs.getString("run_no"),
                    rs.getString("issue_type"),
                    rs.getString("severity"),
                    getLong(rs, "feature_id"),
                    rs.getString("feature_code"),
                    rs.getString("feature_name"),
                    rs.getString("module_name"),
                    rs.getString("target_type"),
                    rs.getString("target_value"),
                    rs.getString("issue_message"),
                    rs.getString("status"),
                    rs.getString("handle_remark"),
                    getLong(rs, "handled_by"),
                    format(rs.getTimestamp("handled_at")),
                    format(rs.getTimestamp("created_at")),
                    format(rs.getTimestamp("updated_at"))
            );
        } catch (Exception ex) {
            throw new BizException("治理问题数据解析失败: " + ex.getMessage());
        }
    }

    private void validateFeature(FeatureRegistryCommand command, boolean creating) {
        if (command == null) {
            throw new BizException("功能参数不能为空");
        }
        if (creating && !hasText(command.featureCode())) {
            throw new BizException("功能编码不能为空");
        }
        if (!hasText(command.featureName())) {
            throw new BizException("功能名称不能为空");
        }
        normalizeFeatureType(command.featureType());
        normalizeRiskLevel(command.riskLevel());
        normalizeStatus(defaultString(command.status(), "ENABLED"));
    }

    private String normalizeFeatureType(String value) {
        String normalized = defaultString(value, "PAGE").toUpperCase(Locale.ROOT);
        if (!FEATURE_TYPES.contains(normalized)) {
            throw new BizException("不支持的功能类型: " + value);
        }
        return normalized;
    }

    private String normalizeRiskLevel(String value) {
        String normalized = defaultString(value, "NORMAL").toUpperCase(Locale.ROOT);
        if (!RISK_LEVELS.contains(normalized)) {
            throw new BizException("不支持的风险等级: " + value);
        }
        return normalized;
    }

    private String normalizeStatus(String value) {
        String normalized = defaultString(value, "ENABLED").toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new BizException("不支持的状态: " + value);
        }
        return normalized;
    }

    private String normalizeApiMethod(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private int safeLimit(Integer limit) {
        return limit == null || limit <= 0 || limit > 5000 ? 100 : limit;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String defaultString(String value, String defaultValue) {
        return hasText(value) ? value.trim() : defaultValue;
    }

    private void setLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setLong(index, value);
        }
    }

    private Long getLong(ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String format(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().format(DATE_TIME);
    }

    private record IssueDraft(
            String runNo,
            String issueType,
            String severity,
            Long featureId,
            String featureCode,
            String featureName,
            String moduleName,
            String targetType,
            String targetValue,
            String issueMessage
    ) {
    }
}
