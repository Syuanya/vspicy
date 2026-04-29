package com.vspicy.video.service;

import com.vspicy.video.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class OperationAuditEnhancedService {
    private final JdbcTemplate jdbcTemplate;

    public OperationAuditEnhancedService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> actions() {
        if (!tableExists("operation_audit_log")) {
            return List.of();
        }

        return jdbcTemplate.query(
                "SELECT DISTINCT action FROM operation_audit_log WHERE action IS NOT NULL AND action <> '' ORDER BY action ASC",
                (rs, rowNum) -> rs.getString("action")
        );
    }

    public List<OperationAuditLogView> recent(Integer limit) {
        return listAdvanced(null, null, null, null, null, null, null, null, normalizeLimit(limit, 50, 200));
    }

    public OperationAuditEvidenceView evidence(Long id, Integer limit) {
        int safeLimit = normalizeLimit(limit, 20, 100);

        if (id == null || !tableExists("operation_audit_log")) {
            return new OperationAuditEvidenceView(null, List.of(), List.of(), List.of(), List.of("审计日志不存在或 operation_audit_log 表未初始化"));
        }

        OperationAuditLogView current = findById(id);
        if (current == null) {
            return new OperationAuditEvidenceView(null, List.of(), List.of(), List.of(), List.of("未找到指定审计日志"));
        }

        List<OperationAuditLogView> targetTimeline = findTargetTimeline(current.targetType(), current.targetId(), safeLimit);
        List<OperationAuditLogView> sameOperatorRecent = findSameOperatorRecent(current.operatorId(), current.operatorName(), safeLimit);
        List<OperationAuditLogView> sameIpRecent = findSameIpRecent(current.requestIp(), safeLimit);

        return new OperationAuditEvidenceView(
                current,
                targetTimeline,
                sameOperatorRecent,
                sameIpRecent,
                buildRiskHints(current, targetTimeline, sameOperatorRecent, sameIpRecent)
        );
    }

    public List<OperationAuditLogView> listAdvanced(
            String action,
            String targetType,
            String targetId,
            Long operatorId,
            String requestIp,
            String startTime,
            String endTime,
            String resultStatus,
            Integer limit
    ) {
        if (!tableExists("operation_audit_log")) {
            return List.of();
        }

        int safeLimit = normalizeLimit(limit, 100, 5000);
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

        if (!isBlank(targetId)) {
            sql.append(" AND target_id = ?");
            args.add(targetId);
        }

        if (operatorId != null) {
            sql.append(" AND operator_id = ?");
            args.add(operatorId);
        }

        if (!isBlank(requestIp)) {
            sql.append(" AND request_ip = ?");
            args.add(requestIp);
        }

        if (!isBlank(startTime)) {
            sql.append(" AND created_at >= ?");
            args.add(normalizeDateTime(startTime));
        }

        if (!isBlank(endTime)) {
            sql.append(" AND created_at <= ?");
            args.add(normalizeDateTime(endTime));
        }

        appendResultStatusFilter(sql, resultStatus);

        sql.append(" ORDER BY id DESC LIMIT ?");
        args.add(safeLimit);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapLog(rs), args.toArray());
    }

    public OperationAuditRiskSummaryView riskSummary(Integer hours, Integer limit) {
        int safeHours = normalizeHours(hours);
        int safeLimit = normalizeLimit(limit, 10, 50);

        if (!tableExists("operation_audit_log")) {
            return new OperationAuditRiskSummaryView(
                    LocalDateTime.now().toString(),
                    safeHours,
                    0L,
                    0L,
                    0L,
                    "success",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        String startAt = LocalDateTime.now()
                .minusHours(safeHours)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        Long totalCount = countSince(startAt, "1 = 1");
        Long rejectedCount = countSince(startAt, rejectedPredicate());
        Long dangerCount = countSince(startAt, dangerPredicate());

        return new OperationAuditRiskSummaryView(
                LocalDateTime.now().toString(),
                safeHours,
                totalCount,
                rejectedCount,
                dangerCount,
                riskLevel(totalCount, rejectedCount, dangerCount),
                actionSummary(startAt, safeLimit),
                operatorSummary(startAt, safeLimit),
                ipSummary(startAt, safeLimit),
                recentRejected(startAt, safeLimit)
        );
    }

    public List<OperationAuditAlertView> alerts(Integer hours, Integer limit) {
        int safeHours = normalizeHours(hours);
        int safeLimit = normalizeLimit(limit, 50, 200);

        if (!tableExists("operation_audit_log")) {
            return List.of();
        }

        String startAt = LocalDateTime.now()
                .minusHours(safeHours)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<OperationAuditAlertView> alerts = new ArrayList<>();
        addRejectedSpikeByIp(alerts, startAt, safeLimit);
        addRejectedSpikeByOperator(alerts, startAt, safeLimit);
        addDangerSpikeByOperator(alerts, startAt, safeLimit);
        addSameTargetRepeatDanger(alerts, startAt, safeLimit);
        addBatchSyncRejected(alerts, startAt);
        addMissingReasonRejected(alerts, startAt);

        alerts.sort(
                Comparator.comparingInt((OperationAuditAlertView item) -> levelWeight(item.alertLevel()))
                        .reversed()
                        .thenComparing(
                                OperationAuditAlertView::count,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
                        .thenComparing(
                                OperationAuditAlertView::lastTime,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
        );

        if (alerts.size() <= safeLimit) {
            return alerts;
        }
        return new ArrayList<>(alerts.subList(0, safeLimit));
    }

    public OperationAuditAlertSummaryView alertSummary(Integer hours, Integer limit) {
        int safeHours = normalizeHours(hours);
        int safeLimit = normalizeLimit(limit, 10, 50);
        List<OperationAuditAlertView> rows = alerts(safeHours, Math.max(safeLimit, 50));

        long critical = rows.stream().filter(item -> "CRITICAL".equalsIgnoreCase(item.alertLevel())).count();
        long danger = rows.stream().filter(item -> "DANGER".equalsIgnoreCase(item.alertLevel())).count();
        long warning = rows.stream().filter(item -> "WARNING".equalsIgnoreCase(item.alertLevel())).count();
        long info = rows.stream().filter(item -> "INFO".equalsIgnoreCase(item.alertLevel())).count();

        List<OperationAuditAlertView> recentAlerts = rows.size() <= safeLimit
                ? rows
                : new ArrayList<>(rows.subList(0, safeLimit));

        return new OperationAuditAlertSummaryView(
                LocalDateTime.now().toString(),
                safeHours,
                (long) rows.size(),
                critical,
                danger,
                warning,
                info,
                highestLevel(critical, danger, warning, info),
                recentAlerts
        );
    }

    public String exportCsv(
            String action,
            String targetType,
            String targetId,
            Long operatorId,
            String requestIp,
            String startTime,
            String endTime,
            String resultStatus,
            Integer limit
    ) {
        List<OperationAuditLogView> rows = listAdvanced(
                action,
                targetType,
                targetId,
                operatorId,
                requestIp,
                startTime,
                endTime,
                resultStatus,
                normalizeLimit(limit, 500, 5000)
        );

        StringBuilder builder = new StringBuilder();
        builder.append("id,action,targetType,targetId,operatorId,operatorName,description,requestIp,userAgent,createdAt,detailJson\n");

        for (OperationAuditLogView row : rows) {
            builder
                    .append(csv(row.id())).append(',')
                    .append(csv(row.action())).append(',')
                    .append(csv(row.targetType())).append(',')
                    .append(csv(row.targetId())).append(',')
                    .append(csv(row.operatorId())).append(',')
                    .append(csv(row.operatorName())).append(',')
                    .append(csv(row.description())).append(',')
                    .append(csv(row.requestIp())).append(',')
                    .append(csv(row.userAgent())).append(',')
                    .append(csv(row.createdAt())).append(',')
                    .append(csv(row.detailJson()))
                    .append('\n');
        }

        return builder.toString();
    }

    private void addRejectedSpikeByIp(List<OperationAuditAlertView> alerts, String startAt, int limit) {
        String sql = """
                SELECT COALESCE(NULLIF(request_ip, ''), '-') AS request_ip,
                       COUNT(*) AS total_count,
                       MIN(created_at) AS first_created_at,
                       MAX(created_at) AS last_created_at,
                       GROUP_CONCAT(id ORDER BY id DESC SEPARATOR ',') AS audit_ids
                FROM operation_audit_log
                WHERE created_at >= ?
                  AND COALESCE(request_ip, '') <> ''
                  AND %s
                GROUP BY COALESCE(NULLIF(request_ip, ''), '-')
                HAVING COUNT(*) >= 3
                ORDER BY total_count DESC, last_created_at DESC
                LIMIT ?
                """.formatted(rejectedPredicate());

        alerts.addAll(jdbcTemplate.query(sql, (rs, rowNum) -> {
            long count = rs.getLong("total_count");
            String requestIp = rs.getString("request_ip");
            return new OperationAuditAlertView(
                    spikeLevel(count, 8, 5),
                    "SAME_IP_REJECTED_SPIKE",
                    "同一 IP 在统计窗口内连续触发 " + count + " 次被拒绝高危操作",
                    null,
                    null,
                    null,
                    null,
                    null,
                    requestIp,
                    count,
                    rs.getString("first_created_at"),
                    rs.getString("last_created_at"),
                    parseIds(rs.getString("audit_ids"), 20),
                    "/admin/operation-audit?resultStatus=REJECTED&requestIp=" + encode(requestIp)
            );
        }, startAt, limit));
    }

    private void addRejectedSpikeByOperator(List<OperationAuditAlertView> alerts, String startAt, int limit) {
        String sql = """
                SELECT operator_id,
                       COALESCE(NULLIF(operator_name, ''), 'system') AS operator_name,
                       COUNT(*) AS total_count,
                       MIN(created_at) AS first_created_at,
                       MAX(created_at) AS last_created_at,
                       GROUP_CONCAT(id ORDER BY id DESC SEPARATOR ',') AS audit_ids
                FROM operation_audit_log
                WHERE created_at >= ?
                  AND %s
                GROUP BY operator_id, COALESCE(NULLIF(operator_name, ''), 'system')
                HAVING COUNT(*) >= 3
                ORDER BY total_count DESC, last_created_at DESC
                LIMIT ?
                """.formatted(rejectedPredicate());

        alerts.addAll(jdbcTemplate.query(sql, (rs, rowNum) -> {
            long count = rs.getLong("total_count");
            Long operatorId = nullableLong(rs.getObject("operator_id"));
            String operatorName = rs.getString("operator_name");
            return new OperationAuditAlertView(
                    spikeLevel(count, 8, 5),
                    "SAME_OPERATOR_REJECTED_SPIKE",
                    "同一操作人在统计窗口内连续触发 " + count + " 次被拒绝高危操作",
                    null,
                    null,
                    null,
                    operatorId,
                    operatorName,
                    null,
                    count,
                    rs.getString("first_created_at"),
                    rs.getString("last_created_at"),
                    parseIds(rs.getString("audit_ids"), 20),
                    auditLink("REJECTED", null, null, operatorId, null)
            );
        }, startAt, limit));
    }

    private void addDangerSpikeByOperator(List<OperationAuditAlertView> alerts, String startAt, int limit) {
        String sql = """
                SELECT operator_id,
                       COALESCE(NULLIF(operator_name, ''), 'system') AS operator_name,
                       COUNT(*) AS total_count,
                       MIN(created_at) AS first_created_at,
                       MAX(created_at) AS last_created_at,
                       GROUP_CONCAT(id ORDER BY id DESC SEPARATOR ',') AS audit_ids
                FROM operation_audit_log
                WHERE created_at >= ?
                  AND %s
                GROUP BY operator_id, COALESCE(NULLIF(operator_name, ''), 'system')
                HAVING COUNT(*) >= 10
                ORDER BY total_count DESC, last_created_at DESC
                LIMIT ?
                """.formatted(dangerPredicate());

        alerts.addAll(jdbcTemplate.query(sql, (rs, rowNum) -> {
            long count = rs.getLong("total_count");
            Long operatorId = nullableLong(rs.getObject("operator_id"));
            String operatorName = rs.getString("operator_name");
            return new OperationAuditAlertView(
                    spikeLevel(count, 20, 10),
                    "SAME_OPERATOR_DANGER_SPIKE",
                    "同一操作人在统计窗口内执行 " + count + " 次高危运维动作",
                    null,
                    null,
                    null,
                    operatorId,
                    operatorName,
                    null,
                    count,
                    rs.getString("first_created_at"),
                    rs.getString("last_created_at"),
                    parseIds(rs.getString("audit_ids"), 20),
                    auditLink("DANGER", null, null, operatorId, null)
            );
        }, startAt, limit));
    }

    private void addSameTargetRepeatDanger(List<OperationAuditAlertView> alerts, String startAt, int limit) {
        String sql = """
                SELECT COALESCE(NULLIF(target_type, ''), '-') AS target_type,
                       COALESCE(NULLIF(target_id, ''), '-') AS target_id,
                       COUNT(*) AS total_count,
                       MIN(created_at) AS first_created_at,
                       MAX(created_at) AS last_created_at,
                       GROUP_CONCAT(id ORDER BY id DESC SEPARATOR ',') AS audit_ids
                FROM operation_audit_log
                WHERE created_at >= ?
                  AND COALESCE(target_type, '') <> ''
                  AND COALESCE(target_id, '') <> ''
                  AND %s
                GROUP BY COALESCE(NULLIF(target_type, ''), '-'), COALESCE(NULLIF(target_id, ''), '-')
                HAVING COUNT(*) >= 3
                ORDER BY total_count DESC, last_created_at DESC
                LIMIT ?
                """.formatted(dangerPredicate());

        alerts.addAll(jdbcTemplate.query(sql, (rs, rowNum) -> {
            long count = rs.getLong("total_count");
            String targetType = rs.getString("target_type");
            String targetId = rs.getString("target_id");
            return new OperationAuditAlertView(
                    spikeLevel(count, 8, 5),
                    "SAME_TARGET_REPEAT_DANGER",
                    "同一 target 在统计窗口内出现 " + count + " 次高危操作，建议复盘状态变化",
                    null,
                    targetType,
                    targetId,
                    null,
                    null,
                    null,
                    count,
                    rs.getString("first_created_at"),
                    rs.getString("last_created_at"),
                    parseIds(rs.getString("audit_ids"), 20),
                    auditLink("DANGER", targetType, targetId, null, null)
            );
        }, startAt, limit));
    }

    private void addBatchSyncRejected(List<OperationAuditAlertView> alerts, String startAt) {
        String sql = """
                SELECT COUNT(*) AS total_count,
                       MIN(created_at) AS first_created_at,
                       MAX(created_at) AS last_created_at,
                       GROUP_CONCAT(id ORDER BY id DESC SEPARATOR ',') AS audit_ids
                FROM operation_audit_log
                WHERE created_at >= ?
                  AND COALESCE(action, '') LIKE '%PLAYBACK_READINESS_BATCH_SYNC%'
                  AND """ + rejectedPredicate();

        jdbcTemplate.query(sql, rs -> {
            long count = rs.getLong("total_count");
            if (count <= 0) {
                return;
            }
            alerts.add(new OperationAuditAlertView(
                    spikeLevel(count, 5, 2),
                    "PLAYBACK_BATCH_SYNC_REJECTED",
                    "播放就绪批量同步在统计窗口内被拒绝 " + count + " 次",
                    "PLAYBACK_READINESS_BATCH_SYNC",
                    null,
                    null,
                    null,
                    null,
                    null,
                    count,
                    rs.getString("first_created_at"),
                    rs.getString("last_created_at"),
                    parseIds(rs.getString("audit_ids"), 20),
                    "/admin/operation-audit?resultStatus=REJECTED&action=PLAYBACK_READINESS_BATCH_SYNC_REJECTED"
            ));
        }, startAt);
    }

    private void addMissingReasonRejected(List<OperationAuditAlertView> alerts, String startAt) {
        String sql = """
                SELECT COUNT(*) AS total_count,
                       MIN(created_at) AS first_created_at,
                       MAX(created_at) AS last_created_at,
                       GROUP_CONCAT(id ORDER BY id DESC SEPARATOR ',') AS audit_ids
                FROM operation_audit_log
                WHERE created_at >= ?
                  AND """ + rejectedPredicate() + """
                  AND (
                      COALESCE(description, '') LIKE '%reason%'
                      OR COALESCE(detail_json, '') LIKE '%reason%'
                      OR COALESCE(detail_json, '') LIKE '%validationMessage%'
                      OR COALESCE(detail_json, '') LIKE '%confirmText%'
                  )
                """;

        jdbcTemplate.query(sql, rs -> {
            long count = rs.getLong("total_count");
            if (count <= 0) {
                return;
            }
            alerts.add(new OperationAuditAlertView(
                    spikeLevel(count, 8, 3),
                    "DANGER_REASON_VALIDATION_REJECTED",
                    "高危操作 reason / confirmText 校验失败 " + count + " 次",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    count,
                    rs.getString("first_created_at"),
                    rs.getString("last_created_at"),
                    parseIds(rs.getString("audit_ids"), 20),
                    "/admin/operation-audit?resultStatus=REJECTED"
            ));
        }, startAt);
    }

    private OperationAuditLogView findById(Long id) {
        if (id == null) {
            return null;
        }

        List<OperationAuditLogView> rows = jdbcTemplate.query("""
                SELECT id, action, target_type, target_id, operator_id, operator_name,
                       description, request_ip, user_agent, detail_json, created_at
                FROM operation_audit_log
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapLog(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<OperationAuditLogView> findTargetTimeline(String targetType, String targetId, int limit) {
        if (isBlank(targetType) || isBlank(targetId)) {
            return List.of();
        }

        return jdbcTemplate.query("""
                SELECT id, action, target_type, target_id, operator_id, operator_name,
                       description, request_ip, user_agent, detail_json, created_at
                FROM operation_audit_log
                WHERE target_type = ?
                  AND target_id = ?
                ORDER BY id DESC
                LIMIT ?
                """, (rs, rowNum) -> mapLog(rs), targetType, targetId, limit);
    }

    private List<OperationAuditLogView> findSameOperatorRecent(Long operatorId, String operatorName, int limit) {
        if (operatorId != null) {
            return jdbcTemplate.query("""
                    SELECT id, action, target_type, target_id, operator_id, operator_name,
                           description, request_ip, user_agent, detail_json, created_at
                    FROM operation_audit_log
                    WHERE operator_id = ?
                    ORDER BY id DESC
                    LIMIT ?
                    """, (rs, rowNum) -> mapLog(rs), operatorId, limit);
        }

        if (isBlank(operatorName)) {
            return List.of();
        }

        return jdbcTemplate.query("""
                SELECT id, action, target_type, target_id, operator_id, operator_name,
                       description, request_ip, user_agent, detail_json, created_at
                FROM operation_audit_log
                WHERE operator_name = ?
                ORDER BY id DESC
                LIMIT ?
                """, (rs, rowNum) -> mapLog(rs), operatorName, limit);
    }

    private List<OperationAuditLogView> findSameIpRecent(String requestIp, int limit) {
        if (isBlank(requestIp) || "-".equals(requestIp)) {
            return List.of();
        }

        return jdbcTemplate.query("""
                SELECT id, action, target_type, target_id, operator_id, operator_name,
                       description, request_ip, user_agent, detail_json, created_at
                FROM operation_audit_log
                WHERE request_ip = ?
                ORDER BY id DESC
                LIMIT ?
                """, (rs, rowNum) -> mapLog(rs), requestIp, limit);
    }

    private List<String> buildRiskHints(
            OperationAuditLogView current,
            List<OperationAuditLogView> targetTimeline,
            List<OperationAuditLogView> sameOperatorRecent,
            List<OperationAuditLogView> sameIpRecent
    ) {
        List<String> hints = new ArrayList<>();
        String action = current.action() == null ? "" : current.action();

        if (isRejectedLog(current)) {
            hints.add("该操作已被后端校验拒绝，优先检查 reason、confirmText、dryRun 参数是否符合高危操作要求");
        }
        if (isDangerLog(current)) {
            hints.add("该操作属于高危运维动作，建议核对操作窗口、影响范围和操作者身份");
        }
        if (targetTimeline.size() >= 3) {
            hints.add("同一 target 存在多次操作记录，建议按时间线复盘状态变化");
        }
        if (sameOperatorRecent.size() >= 10) {
            hints.add("同一操作者近期操作较多，建议结合操作人权限和业务变更单排查");
        }
        if (sameIpRecent.size() >= 10) {
            hints.add("同一 IP 近期操作较多，建议确认是否为可信运维终端或网关出口");
        }
        if (action.contains("SYNC") || action.contains("RESET") || action.contains("RERUN")) {
            hints.add("建议对照业务表状态、播放就绪状态和转码任务状态确认最终结果");
        }

        if (hints.isEmpty()) {
            hints.add("未发现明显风险信号，可继续查看 detailJson 和同 target 时间线");
        }
        return hints;
    }

    private boolean isRejectedLog(OperationAuditLogView row) {
        String action = row.action() == null ? "" : row.action();
        String detail = row.detailJson() == null ? "" : row.detailJson().replace(" ", "");
        return action.endsWith("_REJECTED") || detail.contains("\"rejected\":true");
    }

    private boolean isDangerLog(OperationAuditLogView row) {
        String action = row.action() == null ? "" : row.action();
        return isRejectedLog(row)
                || action.contains("RERUN")
                || action.contains("RESET")
                || action.contains("CANCEL")
                || action.contains("FAIL")
                || action.contains("SYNC")
                || action.contains("CLEANUP")
                || action.contains("REPAIR");
    }

    private OperationAuditLogView mapLog(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OperationAuditLogView(
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
        );
    }

    private List<OperationAuditActionSummaryView> actionSummary(String startAt, int limit) {
        String sql = """
                SELECT action,
                       COUNT(*) AS total_count,
                       SUM(CASE WHEN %s THEN 1 ELSE 0 END) AS rejected_count,
                       SUM(CASE WHEN %s THEN 1 ELSE 0 END) AS danger_count,
                       MAX(created_at) AS last_created_at
                FROM operation_audit_log
                WHERE created_at >= ?
                GROUP BY action
                ORDER BY rejected_count DESC, danger_count DESC, total_count DESC, last_created_at DESC
                LIMIT ?
                """.formatted(rejectedPredicate(), dangerPredicate());

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Long rejected = rs.getLong("rejected_count");
            Long danger = rs.getLong("danger_count");
            Long total = rs.getLong("total_count");
            return new OperationAuditActionSummaryView(
                    rs.getString("action"),
                    total,
                    rejected,
                    danger,
                    riskLevel(total, rejected, danger),
                    rs.getString("last_created_at")
            );
        }, startAt, limit);
    }

    private List<OperationAuditOperatorSummaryView> operatorSummary(String startAt, int limit) {
        String sql = """
                SELECT operator_id,
                       COALESCE(NULLIF(operator_name, ''), 'system') AS operator_name,
                       COUNT(*) AS total_count,
                       SUM(CASE WHEN %s THEN 1 ELSE 0 END) AS rejected_count,
                       SUM(CASE WHEN %s THEN 1 ELSE 0 END) AS danger_count,
                       MAX(created_at) AS last_created_at
                FROM operation_audit_log
                WHERE created_at >= ?
                GROUP BY operator_id, COALESCE(NULLIF(operator_name, ''), 'system')
                ORDER BY rejected_count DESC, danger_count DESC, total_count DESC, last_created_at DESC
                LIMIT ?
                """.formatted(rejectedPredicate(), dangerPredicate());

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Long rejected = rs.getLong("rejected_count");
            Long danger = rs.getLong("danger_count");
            Long total = rs.getLong("total_count");
            return new OperationAuditOperatorSummaryView(
                    nullableLong(rs.getObject("operator_id")),
                    rs.getString("operator_name"),
                    total,
                    rejected,
                    danger,
                    riskLevel(total, rejected, danger),
                    rs.getString("last_created_at")
            );
        }, startAt, limit);
    }

    private List<OperationAuditIpSummaryView> ipSummary(String startAt, int limit) {
        String sql = """
                SELECT COALESCE(NULLIF(request_ip, ''), '-') AS request_ip,
                       COUNT(*) AS total_count,
                       SUM(CASE WHEN %s THEN 1 ELSE 0 END) AS rejected_count,
                       SUM(CASE WHEN %s THEN 1 ELSE 0 END) AS danger_count,
                       MAX(created_at) AS last_created_at
                FROM operation_audit_log
                WHERE created_at >= ?
                GROUP BY COALESCE(NULLIF(request_ip, ''), '-')
                ORDER BY rejected_count DESC, danger_count DESC, total_count DESC, last_created_at DESC
                LIMIT ?
                """.formatted(rejectedPredicate(), dangerPredicate());

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Long rejected = rs.getLong("rejected_count");
            Long danger = rs.getLong("danger_count");
            Long total = rs.getLong("total_count");
            return new OperationAuditIpSummaryView(
                    rs.getString("request_ip"),
                    total,
                    rejected,
                    danger,
                    riskLevel(total, rejected, danger),
                    rs.getString("last_created_at")
            );
        }, startAt, limit);
    }

    private List<OperationAuditLogView> recentRejected(String startAt, int limit) {
        return listAdvanced(null, null, null, null, null, startAt, null, "REJECTED", limit);
    }

    private Long countSince(String startAt, String predicate) {
        try {
            Long value = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM operation_audit_log WHERE created_at >= ? AND (" + predicate + ")",
                    Long.class,
                    startAt
            );
            return value == null ? 0L : value;
        } catch (Exception ex) {
            return 0L;
        }
    }

    private String rejectedPredicate() {
        return "(RIGHT(COALESCE(action, ''), 9) = '_REJECTED' " +
                "OR REPLACE(COALESCE(detail_json, ''), ' ', '') LIKE '%\"rejected\":true%')";
    }

    private String dangerPredicate() {
        return "(RIGHT(COALESCE(action, ''), 9) = '_REJECTED' " +
                "OR COALESCE(action, '') LIKE '%RERUN%' " +
                "OR COALESCE(action, '') LIKE '%RESET%' " +
                "OR COALESCE(action, '') LIKE '%CANCEL%' " +
                "OR COALESCE(action, '') LIKE '%FAIL%' " +
                "OR COALESCE(action, '') LIKE '%SYNC%' " +
                "OR COALESCE(action, '') LIKE '%CLEANUP%' " +
                "OR COALESCE(action, '') LIKE '%REPAIR%')";
    }

    private String riskLevel(Long total, Long rejected, Long danger) {
        long totalValue = total == null ? 0L : total;
        long rejectedValue = rejected == null ? 0L : rejected;
        long dangerValue = danger == null ? 0L : danger;

        if (rejectedValue >= 5 || dangerValue >= 20) {
            return "critical";
        }
        if (rejectedValue > 0 || dangerValue >= 10) {
            return "danger";
        }
        if (dangerValue > 0 || totalValue > 0) {
            return "warning";
        }
        return "success";
    }

    private String spikeLevel(long count, long criticalThreshold, long dangerThreshold) {
        if (count >= criticalThreshold) {
            return "CRITICAL";
        }
        if (count >= dangerThreshold) {
            return "DANGER";
        }
        return "WARNING";
    }

    private String highestLevel(long critical, long danger, long warning, long info) {
        if (critical > 0) {
            return "CRITICAL";
        }
        if (danger > 0) {
            return "DANGER";
        }
        if (warning > 0) {
            return "WARNING";
        }
        if (info > 0) {
            return "INFO";
        }
        return "SUCCESS";
    }

    private int levelWeight(String level) {
        if ("CRITICAL".equalsIgnoreCase(level)) {
            return 4;
        }
        if ("DANGER".equalsIgnoreCase(level)) {
            return 3;
        }
        if ("WARNING".equalsIgnoreCase(level)) {
            return 2;
        }
        if ("INFO".equalsIgnoreCase(level)) {
            return 1;
        }
        return 0;
    }

    private void appendResultStatusFilter(StringBuilder sql, String resultStatus) {
        if (isBlank(resultStatus) || "ALL".equalsIgnoreCase(resultStatus)) {
            return;
        }

        String normalized = resultStatus.trim().toUpperCase();
        if ("REJECTED".equals(normalized)) {
            sql.append("""
                     AND (RIGHT(COALESCE(action, ''), 9) = '_REJECTED'
                          OR REPLACE(COALESCE(detail_json, ''), ' ', '') LIKE '%\"rejected\":true%')
                    """);
            return;
        }

        if ("DANGER".equals(normalized)) {
            sql.append("""
                     AND (RIGHT(COALESCE(action, ''), 9) = '_REJECTED'
                          OR COALESCE(action, '') LIKE '%RERUN%'
                          OR COALESCE(action, '') LIKE '%RESET%'
                          OR COALESCE(action, '') LIKE '%CANCEL%'
                          OR COALESCE(action, '') LIKE '%FAIL%'
                          OR COALESCE(action, '') LIKE '%SYNC%'
                          OR COALESCE(action, '') LIKE '%CLEANUP%'
                          OR COALESCE(action, '') LIKE '%REPAIR%')
                    """);
            return;
        }

        if ("SUCCESS".equals(normalized)) {
            sql.append("""
                     AND RIGHT(COALESCE(action, ''), 9) <> '_REJECTED'
                     AND COALESCE(action, '') NOT LIKE '%FAILED%'
                     AND COALESCE(action, '') NOT LIKE '%FAIL%'
                    """);
        }
    }

    private String auditLink(String resultStatus, String targetType, String targetId, Long operatorId, String requestIp) {
        StringBuilder link = new StringBuilder("/admin/operation-audit");
        String separator = "?";
        if (!isBlank(resultStatus)) {
            link.append(separator).append("resultStatus=").append(encode(resultStatus));
            separator = "&";
        }
        if (!isBlank(targetType)) {
            link.append(separator).append("targetType=").append(encode(targetType));
            separator = "&";
        }
        if (!isBlank(targetId)) {
            link.append(separator).append("targetId=").append(encode(targetId));
            separator = "&";
        }
        if (operatorId != null) {
            link.append(separator).append("operatorId=").append(operatorId);
            separator = "&";
        }
        if (!isBlank(requestIp)) {
            link.append(separator).append("requestIp=").append(encode(requestIp));
        }
        return link.toString();
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

    private String encode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String normalizeDateTime(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().replace("T", " ");
        if (normalized.length() == 16) {
            normalized = normalized + ":00";
        }
        return normalized;
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

    private String csv(Object value) {
        if (value == null) {
            return "";
        }

        String text = String.valueOf(value);
        boolean needQuote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");

        String escaped = text.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
