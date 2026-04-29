package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.SchemaDiagnosticColumnView;
import com.vspicy.video.dto.SchemaDiagnosticTableView;
import com.vspicy.video.dto.SchemaDiagnosticView;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Local development diagnostics.
 *
 * This controller is intentionally always registered during development builds.
 * It only reads information_schema and returns schema structure metadata.
 */
@RestController
@RequestMapping("/api/videos/dev")
public class SchemaDiagnosticsController {
    private final JdbcTemplate jdbcTemplate;

    public SchemaDiagnosticsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void logRegistered() {
        System.out.println("SchemaDiagnosticsController registered: /api/videos/dev/ping, /api/videos/dev/schema-diagnostics");
    }

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.ok("schema diagnostics controller registered");
    }

    @GetMapping("/schema-diagnostics")
    public Result<SchemaDiagnosticView> diagnostics() {
        List<TableRequirement> requirements = requirements();
        List<SchemaDiagnosticTableView> tables = new ArrayList<>();
        int missingTables = 0;
        int missingColumns = 0;

        for (TableRequirement requirement : requirements) {
            boolean exists = tableExists(requirement.tableName());
            if (!exists) {
                missingTables++;
            }

            List<SchemaDiagnosticColumnView> columns = new ArrayList<>();
            List<String> missing = new ArrayList<>();

            for (String column : requirement.columns()) {
                SchemaDiagnosticColumnView view = columnView(requirement.tableName(), column);
                columns.add(view);
                if (!Boolean.TRUE.equals(view.exists())) {
                    missing.add(column);
                    missingColumns++;
                }
            }

            tables.add(new SchemaDiagnosticTableView(
                    requirement.tableName(),
                    exists,
                    exists ? rowCount(requirement.tableName()) : 0L,
                    columns,
                    missing
            ));
        }

        return Result.ok(new SchemaDiagnosticView(
                databaseName(),
                requirements.size(),
                missingTables,
                missingColumns,
                tables
        ));
    }

    private List<TableRequirement> requirements() {
        return List.of(
                new TableRequirement("sys_permission", List.of(
                        "id", "parent_id", "permission_code", "permission_name", "permission_type",
                        "path", "component", "icon", "sort_no", "status", "created_at", "updated_at"
                )),
                new TableRequirement("sys_role", List.of(
                        "id", "role_code", "role_name", "description", "status", "created_at", "updated_at"
                )),
                new TableRequirement("sys_role_permission", List.of(
                        "id", "role_id", "permission_id", "created_at"
                )),
                new TableRequirement("user_behavior_log", List.of(
                        "id", "user_id", "target_id", "target_type", "action_type",
                        "duration_seconds", "extra_json", "client_ip", "user_agent", "created_at"
                )),
                new TableRequirement("video_storage_alert_event", List.of(
                        "id", "dedup_key", "alert_code", "alert_level", "title", "content",
                        "target_type", "target_id", "object_key", "user_id", "video_id", "source",
                        "status", "first_seen_at", "last_seen_at", "acked_at", "resolved_at",
                        "created_at", "updated_at"
                )),
                new TableRequirement("video_storage_alert_notification_outbox", List.of(
                        "id", "alert_id", "dedup_key", "target_user_id", "title", "content",
                        "alert_level", "alert_code", "object_key", "status", "notification_table",
                        "notification_id", "error_message", "retry_count", "sent_at", "created_at", "updated_at"
                )),
                new TableRequirement("operation_audit_alert_event", List.of(
                        "id", "dedup_key", "alert_type", "alert_level", "title", "message",
                        "action", "target_type", "target_id", "operator_id", "operator_name", "request_ip",
                        "alert_count", "evidence_audit_ids", "link", "status", "first_seen_at",
                        "last_seen_at", "acked_at", "resolved_at", "created_at", "updated_at"
                )),
                new TableRequirement("video_hls_repair_task", List.of(
                        "id", "dedup_key", "repair_type", "status", "priority", "bucket",
                        "manifest_object_key", "missing_segments", "missing_segment_count",
                        "video_id", "record_id", "trace_id", "alert_id", "source", "retry_count",
                        "max_retry_count", "last_error", "dispatch_topic", "dispatch_tag",
                        "dispatch_payload", "dispatch_message_id", "execute_mode", "execute_payload",
                        "executor_bean", "executor_method", "consumer_message_id", "verify_status",
                        "verify_message", "verify_payload", "dispatched_at", "started_at",
                        "finished_at", "verified_at", "created_at", "updated_at"
                )),
                new TableRequirement("video_object_cleanup_request", List.of(
                        "id", "dedup_key", "bucket", "object_key", "object_size", "issue_type",
                        "source", "status", "reason", "approve_user_id", "reject_user_id",
                        "execute_user_id", "error_message", "approved_at", "rejected_at",
                        "executed_at", "created_at", "updated_at"
                )),
                new TableRequirement("video_transcode_task", List.of(
                        "id", "video_id", "source_file_path", "status", "error_message", "created_at", "updated_at"
                ))
        );
    }

    private boolean tableExists(String tableName) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Long.class,
                tableName
        );
        return value != null && value > 0;
    }

    private SchemaDiagnosticColumnView columnView(String tableName, String columnName) {
        List<SchemaDiagnosticColumnView> rows = jdbcTemplate.query(
                "SELECT column_name, data_type, column_type, is_nullable FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ? LIMIT 1",
                (rs, rowNum) -> new SchemaDiagnosticColumnView(
                        tableName,
                        rs.getString("column_name"),
                        true,
                        rs.getString("data_type"),
                        rs.getString("column_type"),
                        rs.getString("is_nullable")
                ),
                tableName,
                columnName
        );

        if (rows.isEmpty()) {
            return new SchemaDiagnosticColumnView(tableName, columnName, false, "", "", "");
        }

        return rows.get(0);
    }

    private long rowCount(String tableName) {
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]+")) {
            return 0L;
        }
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `" + tableName + "`", Long.class);
        return value == null ? 0L : value;
    }

    private String databaseName() {
        String value = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        return value == null ? "" : value;
    }

    private record TableRequirement(String tableName, List<String> columns) {
    }
}
