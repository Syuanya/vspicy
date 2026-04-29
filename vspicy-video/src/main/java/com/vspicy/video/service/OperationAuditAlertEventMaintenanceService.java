package com.vspicy.video.service;

import com.vspicy.video.dto.OperationAuditAlertEventCleanupCommand;
import com.vspicy.video.dto.OperationAuditAlertEventCleanupResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationAuditAlertEventMaintenanceService {
    private static final String TABLE_NAME = "operation_audit_alert_event";

    private final JdbcTemplate jdbcTemplate;

    public OperationAuditAlertEventMaintenanceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public OperationAuditAlertEventCleanupResult cleanupResolved(OperationAuditAlertEventCleanupCommand command) {
        if (!tableExists(TABLE_NAME)) {
            return new OperationAuditAlertEventCleanupResult(
                    retentionDays(command),
                    limit(command),
                    dryRun(command),
                    0L,
                    0L,
                    "operation_audit_alert_event 表不存在，请先执行 Phase84 SQL"
            );
        }

        int retentionDays = retentionDays(command);
        int limit = limit(command);
        boolean dryRun = dryRun(command);

        String predicate = """
                status = 'RESOLVED'
                AND resolved_at IS NOT NULL
                AND resolved_at < TIMESTAMPADD(DAY, -%d, NOW())
                """.formatted(retentionDays);

        Long candidateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE " + predicate,
                Long.class
        );
        if (candidateCount == null) {
            candidateCount = 0L;
        }

        if (dryRun) {
            return new OperationAuditAlertEventCleanupResult(
                    retentionDays,
                    limit,
                    true,
                    candidateCount,
                    0L,
                    "dryRun 完成，未删除任何告警事件"
            );
        }

        int deleted = jdbcTemplate.update(
                "DELETE FROM " + TABLE_NAME + " WHERE " + predicate + " ORDER BY resolved_at ASC, id ASC LIMIT ?",
                limit
        );

        return new OperationAuditAlertEventCleanupResult(
                retentionDays,
                limit,
                false,
                candidateCount,
                (long) deleted,
                "已清理 RESOLVED 历史告警事件"
        );
    }

    private int retentionDays(OperationAuditAlertEventCleanupCommand command) {
        Integer value = command == null ? null : command.retentionDays();
        if (value == null || value <= 0) {
            return 30;
        }
        return Math.min(value, 365);
    }

    private int limit(OperationAuditAlertEventCleanupCommand command) {
        Integer value = command == null ? null : command.limit();
        if (value == null || value <= 0) {
            return 500;
        }
        return Math.min(value, 5000);
    }

    private boolean dryRun(OperationAuditAlertEventCleanupCommand command) {
        return command == null || command.dryRun() == null || Boolean.TRUE.equals(command.dryRun());
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
}
