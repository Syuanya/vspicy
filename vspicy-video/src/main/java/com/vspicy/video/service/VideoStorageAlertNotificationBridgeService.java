package com.vspicy.video.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.dto.VideoStorageAlertNotificationSyncCommand;
import com.vspicy.video.dto.VideoStorageAlertNotificationSyncResult;
import com.vspicy.video.dto.VideoStorageAlertNotificationView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class VideoStorageAlertNotificationBridgeService {
    private final JdbcTemplate jdbcTemplate;

    public VideoStorageAlertNotificationBridgeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<VideoStorageAlertNotificationView> list(String status, Integer limit) {
        String safeStatus = status == null ? "" : status.trim();
        int safeLimit = normalizeLimit(limit);

        if (safeStatus.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT id, alert_id, dedup_key, target_user_id, title, content,
                           alert_level, alert_code, object_key, status,
                           notification_table, notification_id, error_message, retry_count,
                           sent_at, created_at, updated_at
                    FROM video_storage_alert_notification_outbox
                    ORDER BY id DESC
                    LIMIT ?
                    """, (rs, rowNum) -> mapOutbox(rs), safeLimit);
        }

        return jdbcTemplate.query("""
                SELECT id, alert_id, dedup_key, target_user_id, title, content,
                       alert_level, alert_code, object_key, status,
                       notification_table, notification_id, error_message, retry_count,
                       sent_at, created_at, updated_at
                FROM video_storage_alert_notification_outbox
                WHERE status = ?
                ORDER BY id DESC
                LIMIT ?
                """, (rs, rowNum) -> mapOutbox(rs), safeStatus, safeLimit);
    }

    @Transactional
    public VideoStorageAlertNotificationSyncResult sync(VideoStorageAlertNotificationSyncCommand command) {
        int limit = command == null || command.limit() == null ? 100 : normalizeLimit(command.limit());
        String level = command == null || command.level() == null ? "" : command.level().trim();
        Long targetUserId = command == null ? null : command.targetUserId();

        List<AlertRow> alerts = loadOpenAlerts(level, limit);
        long created = 0L;

        for (AlertRow alert : alerts) {
            Long userId = targetUserId != null ? targetUserId : (alert.userId() != null ? alert.userId() : 1L);
            int affected = jdbcTemplate.update("""
                    INSERT IGNORE INTO video_storage_alert_notification_outbox(
                      alert_id, dedup_key, target_user_id, title, content,
                      alert_level, alert_code, object_key, status
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                    """, alert.id(), alert.dedupKey(), userId, alert.title(), alert.content(),
                    alert.alertLevel(), alert.alertCode(), alert.objectKey());

            if (affected > 0) {
                created++;
            }
        }

        NotificationTable notificationTable = detectNotificationTable();
        List<OutboxRow> pending = loadPending(limit);

        long sent = 0L;
        long skipped = 0L;
        long failed = 0L;

        for (OutboxRow row : pending) {
            SendOutcome outcome = sendToNotification(row, notificationTable);
            if ("SENT".equals(outcome.status())) {
                sent++;
            } else if ("SKIPPED".equals(outcome.status())) {
                skipped++;
            } else {
                failed++;
            }
        }

        return new VideoStorageAlertNotificationSyncResult(
                (long) alerts.size(),
                created,
                sent,
                skipped,
                failed,
                notificationTable == null ? null : notificationTable.tableName(),
                "存储告警通知同步完成"
        );
    }

    @Transactional
    public VideoStorageAlertNotificationView retry(Long id) {
        if (id == null) {
            throw new BizException("id 不能为空");
        }

        jdbcTemplate.update("""
                UPDATE video_storage_alert_notification_outbox
                SET status = 'PENDING',
                    retry_count = retry_count + 1,
                    error_message = NULL
                WHERE id = ?
                  AND status IN ('FAILED', 'SKIPPED')
                """, id);

        NotificationTable notificationTable = detectNotificationTable();
        OutboxRow row = loadOutbox(id);
        sendToNotification(row, notificationTable);
        return findById(id);
    }

    private List<AlertRow> loadOpenAlerts(String level, int limit) {
        if (level == null || level.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT id, dedup_key, alert_code, alert_level, title, content,
                           object_key, user_id
                    FROM video_storage_alert_event
                    WHERE status = 'OPEN'
                    ORDER BY
                      CASE alert_level
                        WHEN 'CRITICAL' THEN 1
                        WHEN 'HIGH' THEN 2
                        WHEN 'WARN' THEN 3
                        ELSE 4
                      END,
                      last_seen_at DESC
                    LIMIT ?
                    """, (rs, rowNum) -> new AlertRow(
                    rs.getLong("id"),
                    rs.getString("dedup_key"),
                    rs.getString("alert_code"),
                    rs.getString("alert_level"),
                    rs.getString("title"),
                    rs.getString("content"),
                    rs.getString("object_key"),
                    nullableLong(rs.getObject("user_id"))
            ), limit);
        }

        return jdbcTemplate.query("""
                SELECT id, dedup_key, alert_code, alert_level, title, content,
                       object_key, user_id
                FROM video_storage_alert_event
                WHERE status = 'OPEN'
                  AND alert_level = ?
                ORDER BY last_seen_at DESC
                LIMIT ?
                """, (rs, rowNum) -> new AlertRow(
                rs.getLong("id"),
                rs.getString("dedup_key"),
                rs.getString("alert_code"),
                rs.getString("alert_level"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("object_key"),
                nullableLong(rs.getObject("user_id"))
        ), level, limit);
    }

    private List<OutboxRow> loadPending(int limit) {
        return jdbcTemplate.query("""
                SELECT id, alert_id, target_user_id, title, content, alert_level, alert_code, object_key
                FROM video_storage_alert_notification_outbox
                WHERE status = 'PENDING'
                ORDER BY id ASC
                LIMIT ?
                """, (rs, rowNum) -> new OutboxRow(
                rs.getLong("id"),
                rs.getLong("alert_id"),
                rs.getLong("target_user_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("alert_level"),
                rs.getString("alert_code"),
                rs.getString("object_key")
        ), limit);
    }

    private OutboxRow loadOutbox(Long id) {
        List<OutboxRow> rows = jdbcTemplate.query("""
                SELECT id, alert_id, target_user_id, title, content, alert_level, alert_code, object_key
                FROM video_storage_alert_notification_outbox
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> new OutboxRow(
                rs.getLong("id"),
                rs.getLong("alert_id"),
                rs.getLong("target_user_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("alert_level"),
                rs.getString("alert_code"),
                rs.getString("object_key")
        ), id);

        if (rows.isEmpty()) {
            throw new BizException("outbox 记录不存在");
        }

        return rows.get(0);
    }

    private SendOutcome sendToNotification(OutboxRow row, NotificationTable table) {
        if (table == null) {
            markSkipped(row.id(), "未找到兼容 notification 表");
            return new SendOutcome("SKIPPED", null, "未找到兼容 notification 表");
        }

        try {
            InsertPlan plan = buildInsertPlan(row, table);
            if (plan.columns().isEmpty()) {
                markSkipped(row.id(), "notification 表字段不足，无法写入");
                return new SendOutcome("SKIPPED", table.tableName(), "notification 表字段不足");
            }

            String sql = "INSERT INTO `" + table.tableName() + "`("
                    + String.join(",", plan.columns())
                    + ") VALUES ("
                    + String.join(",", Collections.nCopies(plan.columns().size(), "?"))
                    + ")";

            jdbcTemplate.update(sql, plan.values().toArray());

            jdbcTemplate.update("""
                    UPDATE video_storage_alert_notification_outbox
                    SET status = 'SENT',
                        notification_table = ?,
                        sent_at = NOW(),
                        error_message = NULL
                    WHERE id = ?
                    """, table.tableName(), row.id());

            return new SendOutcome("SENT", table.tableName(), null);
        } catch (Exception ex) {
            jdbcTemplate.update("""
                    UPDATE video_storage_alert_notification_outbox
                    SET status = 'FAILED',
                        notification_table = ?,
                        error_message = ?
                    WHERE id = ?
                    """, table.tableName(), ex.getMessage(), row.id());
            return new SendOutcome("FAILED", table.tableName(), ex.getMessage());
        }
    }

    private InsertPlan buildInsertPlan(OutboxRow row, NotificationTable table) {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        addIfExists(table, columns, values, "user_id", row.targetUserId());
        addIfExists(table, columns, values, "target_user_id", row.targetUserId());
        addIfExists(table, columns, values, "receiver_id", row.targetUserId());
        addIfExists(table, columns, values, "to_user_id", row.targetUserId());

        addIfExists(table, columns, values, "title", row.title());
        addIfExists(table, columns, values, "content", row.content());
        addIfExists(table, columns, values, "message", row.content());

        addIfExists(table, columns, values, "type", "STORAGE_ALERT");
        addIfExists(table, columns, values, "notification_type", "STORAGE_ALERT");
        addIfExists(table, columns, values, "biz_type", "STORAGE_ALERT");

        addIfExists(table, columns, values, "biz_id", row.alertId());
        addIfExists(table, columns, values, "source_id", row.alertId());
        addIfExists(table, columns, values, "ref_id", row.alertId());

        addIfExists(table, columns, values, "level", row.alertLevel());
        addIfExists(table, columns, values, "status", "UNREAD");
        addIfExists(table, columns, values, "read_status", "UNREAD");
        addIfExists(table, columns, values, "is_read", 0);

        addIfExists(table, columns, values, "created_at", new java.sql.Timestamp(System.currentTimeMillis()));
        addIfExists(table, columns, values, "updated_at", new java.sql.Timestamp(System.currentTimeMillis()));

        boolean hasTitle = table.hasColumn("title") || table.hasColumn("content") || table.hasColumn("message");
        boolean hasUser = table.hasColumn("user_id") || table.hasColumn("target_user_id") || table.hasColumn("receiver_id") || table.hasColumn("to_user_id");

        if (!hasTitle || !hasUser) {
            return new InsertPlan(List.of(), List.of());
        }

        return new InsertPlan(columns, values);
    }

    private void addIfExists(NotificationTable table, List<String> columns, List<Object> values, String column, Object value) {
        if (table.hasColumn(column)) {
            columns.add("`" + table.actualColumn(column) + "`");
            values.add(value);
        }
    }

    private NotificationTable detectNotificationTable() {
        String[] candidates = {"notification", "notifications", "user_notification", "sys_notification"};
        for (String tableName : candidates) {
            if (!tableExists(tableName)) {
                continue;
            }

            List<String> columns = jdbcTemplate.query("""
                    SELECT column_name
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                    """, (rs, rowNum) -> rs.getString("column_name"), tableName);

            NotificationTable table = new NotificationTable(tableName, columns);
            boolean hasTitle = table.hasColumn("title") || table.hasColumn("content") || table.hasColumn("message");
            boolean hasUser = table.hasColumn("user_id") || table.hasColumn("target_user_id") || table.hasColumn("receiver_id") || table.hasColumn("to_user_id");

            if (hasTitle && hasUser) {
                return table;
            }
        }

        return null;
    }

    private boolean tableExists(String tableName) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, Long.class, tableName);
        return count != null && count > 0;
    }

    private void markSkipped(Long id, String message) {
        jdbcTemplate.update("""
                UPDATE video_storage_alert_notification_outbox
                SET status = 'SKIPPED',
                    error_message = ?
                WHERE id = ?
                """, message, id);
    }

    private VideoStorageAlertNotificationView findById(Long id) {
        List<VideoStorageAlertNotificationView> rows = jdbcTemplate.query("""
                SELECT id, alert_id, dedup_key, target_user_id, title, content,
                       alert_level, alert_code, object_key, status,
                       notification_table, notification_id, error_message, retry_count,
                       sent_at, created_at, updated_at
                FROM video_storage_alert_notification_outbox
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapOutbox(rs), id);

        if (rows.isEmpty()) {
            throw new BizException("outbox 记录不存在");
        }
        return rows.get(0);
    }

    private VideoStorageAlertNotificationView mapOutbox(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new VideoStorageAlertNotificationView(
                rs.getLong("id"),
                rs.getLong("alert_id"),
                rs.getString("dedup_key"),
                rs.getLong("target_user_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("alert_level"),
                rs.getString("alert_code"),
                rs.getString("object_key"),
                rs.getString("status"),
                rs.getString("notification_table"),
                rs.getString("notification_id"),
                rs.getString("error_message"),
                rs.getInt("retry_count"),
                rs.getString("sent_at"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 || limit > 500 ? 100 : limit;
    }

    private record AlertRow(
            Long id,
            String dedupKey,
            String alertCode,
            String alertLevel,
            String title,
            String content,
            String objectKey,
            Long userId
    ) {
    }

    private record OutboxRow(
            Long id,
            Long alertId,
            Long targetUserId,
            String title,
            String content,
            String alertLevel,
            String alertCode,
            String objectKey
    ) {
    }

    private record SendOutcome(String status, String tableName, String error) {
    }

    private record InsertPlan(List<String> columns, List<Object> values) {
    }

    private static final class NotificationTable {
        private final String tableName;
        private final Map<String, String> actualByLower;

        NotificationTable(String tableName, List<String> columns) {
            this.tableName = tableName;
            this.actualByLower = new LinkedHashMap<>();
            for (String column : columns) {
                this.actualByLower.put(column.toLowerCase(Locale.ROOT), column);
            }
        }

        String tableName() {
            return tableName;
        }

        boolean hasColumn(String column) {
            return actualByLower.containsKey(column.toLowerCase(Locale.ROOT));
        }

        String actualColumn(String column) {
            return actualByLower.get(column.toLowerCase(Locale.ROOT));
        }
    }
}
