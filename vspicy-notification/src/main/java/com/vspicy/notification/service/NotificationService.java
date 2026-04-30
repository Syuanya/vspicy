package com.vspicy.notification.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.notification.dto.NotificationAdminInboxItem;
import com.vspicy.notification.dto.NotificationAdminInboxSummaryView;
import com.vspicy.notification.dto.NotificationBatchCommand;
import com.vspicy.notification.dto.NotificationCreateCommand;
import com.vspicy.notification.dto.NotificationInboxItem;
import com.vspicy.notification.dto.NotificationSseEvent;
import com.vspicy.notification.dto.UnreadCountResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationService {
    private final JdbcTemplate jdbcTemplate;
    private final NotificationSseService sseService;
    private final NotificationPreferenceService preferenceService;

    public NotificationService(
            JdbcTemplate jdbcTemplate,
            NotificationSseService sseService,
            NotificationPreferenceService preferenceService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.sseService = sseService;
        this.preferenceService = preferenceService;
    }

    @Transactional
    public Long publishSystem(NotificationCreateCommand command, Long senderId) {
        if (command == null || command.title() == null || command.title().isBlank()) {
            throw new BizException("通知标题不能为空");
        }
        if (command.content() == null || command.content().isBlank()) {
            throw new BizException("通知内容不能为空");
        }

        String notificationType = blankToDefault(command.notificationType(), "SYSTEM").trim().toUpperCase();
        String priority = blankToDefault(command.priority(), "NORMAL");
        String scope = command.receiverUserIds() == null || command.receiverUserIds().isEmpty() ? "ALL" : "USER";

        Long messageId = insertMessage(command, notificationType, priority, scope, senderId);

        if ("ALL".equals(scope)) {
            List<Long> userIds = jdbcTemplate.query(
                    "SELECT id FROM sys_user WHERE status = 1 LIMIT 10000",
                    (rs, rowNum) -> rs.getLong("id")
            );
            for (Long userId : userIds) {
                deliverToUserIfEnabled(userId, messageId, command, notificationType, priority);
            }
        } else {
            for (Long userId : command.receiverUserIds()) {
                if (userId != null) {
                    deliverToUserIfEnabled(userId, messageId, command, notificationType, priority);
                }
            }
        }

        return messageId;
    }

    public List<NotificationInboxItem> inbox(Long userId, Integer readStatus, Integer limit) {
        if (userId == null) {
            throw new BizException("userId 不能为空");
        }
        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 100 : limit;

        String condition = "";
        Object[] params;
        if (readStatus == null) {
            params = new Object[]{userId, safeLimit};
        } else {
            condition = " AND i.read_status = ? ";
            params = new Object[]{userId, readStatus, safeLimit};
        }

        String sql = """
                SELECT
                  i.id AS inbox_id,
                  i.message_id,
                  i.user_id,
                  m.title,
                  m.content,
                  m.notification_type,
                  m.biz_type,
                  m.biz_id,
                  m.priority,
                  i.read_status,
                  i.read_at,
                  i.created_at
                FROM notification_inbox i
                JOIN notification_message m ON m.id = i.message_id
                WHERE i.user_id = ?
                  AND i.deleted = 0
                """ + condition + """
                ORDER BY i.id DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new NotificationInboxItem(
                rs.getLong("inbox_id"),
                rs.getLong("message_id"),
                rs.getLong("user_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("notification_type"),
                rs.getString("biz_type"),
                getNullableLong(rs.getObject("biz_id")),
                rs.getString("priority"),
                rs.getInt("read_status"),
                rs.getString("read_at"),
                rs.getString("created_at")
        ), params);
    }

    public UnreadCountResponse unreadCount(Long userId) {
        if (userId == null) {
            throw new BizException("userId 不能为空");
        }

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_inbox WHERE user_id = ? AND read_status = 0 AND deleted = 0",
                Long.class,
                userId
        );
        return new UnreadCountResponse(userId, count == null ? 0 : count);
    }

    public void markRead(Long userId, Long inboxId) {
        if (userId == null || inboxId == null) {
            throw new BizException("参数不完整");
        }

        jdbcTemplate.update("""
                UPDATE notification_inbox
                SET read_status = 1,
                    read_at = NOW()
                WHERE id = ?
                  AND user_id = ?
                  AND deleted = 0
                """, inboxId, userId);
    }


    public int markBatchRead(Long userId, NotificationBatchCommand command) {
        List<Long> ids = normalizeInboxIds(command);
        if (userId == null || ids.isEmpty()) {
            throw new BizException("参数不完整");
        }

        String placeholders = placeholders(ids.size());
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.addAll(ids);

        return jdbcTemplate.update("""
                UPDATE notification_inbox
                SET read_status = 1,
                    read_at = NOW()
                WHERE user_id = ?
                  AND deleted = 0
                  AND read_status = 0
                  AND id IN (
                """ + placeholders + ")", params.toArray());
    }

    public void markAllRead(Long userId) {
        if (userId == null) {
            throw new BizException("userId 不能为空");
        }

        jdbcTemplate.update("""
                UPDATE notification_inbox
                SET read_status = 1,
                    read_at = NOW()
                WHERE user_id = ?
                  AND read_status = 0
                  AND deleted = 0
                """, userId);
    }

    public void deleteInbox(Long userId, Long inboxId) {
        if (userId == null || inboxId == null) {
            throw new BizException("参数不完整");
        }

        jdbcTemplate.update("""
                UPDATE notification_inbox
                SET deleted = 1
                WHERE id = ?
                  AND user_id = ?
                """, inboxId, userId);
    }


    public int deleteBatch(Long userId, NotificationBatchCommand command) {
        List<Long> ids = normalizeInboxIds(command);
        if (userId == null || ids.isEmpty()) {
            throw new BizException("参数不完整");
        }

        String placeholders = placeholders(ids.size());
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.addAll(ids);

        return jdbcTemplate.update("""
                UPDATE notification_inbox
                SET deleted = 1
                WHERE user_id = ?
                  AND deleted = 0
                  AND id IN (
                """ + placeholders + ")", params.toArray());
    }

    public int clearRead(Long userId) {
        if (userId == null) {
            throw new BizException("userId 不能为空");
        }

        return jdbcTemplate.update("""
                UPDATE notification_inbox
                SET deleted = 1
                WHERE user_id = ?
                  AND deleted = 0
                  AND read_status = 1
                """, userId);
    }

    public List<NotificationAdminInboxItem> adminInbox(
            Long userId,
            Integer readStatus,
            Integer deleted,
            String notificationType,
            String keyword,
            Integer limit
    ) {
        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 100 : limit;
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");

        if (userId != null && userId > 0) {
            where.append(" AND i.user_id = ? ");
            params.add(userId);
        }
        if (readStatus != null) {
            where.append(" AND i.read_status = ? ");
            params.add(readStatus);
        }
        if (deleted != null) {
            where.append(" AND i.deleted = ? ");
            params.add(deleted);
        }
        if (notificationType != null && !notificationType.isBlank()) {
            where.append(" AND m.notification_type = ? ");
            params.add(notificationType.trim().toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (m.title LIKE ? OR m.content LIKE ? OR u.username LIKE ? OR u.nickname LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        params.add(safeLimit);

        String sql = """
                SELECT
                  i.id AS inbox_id,
                  i.message_id,
                  i.user_id,
                  u.username,
                  u.nickname,
                  m.title,
                  m.content,
                  m.notification_type,
                  m.biz_type,
                  m.biz_id,
                  m.priority,
                  m.publish_scope,
                  m.status AS message_status,
                  i.read_status,
                  i.read_at,
                  i.deleted,
                  i.created_at AS delivered_at,
                  m.sender_id,
                  m.created_at AS message_created_at
                FROM notification_inbox i
                JOIN notification_message m ON m.id = i.message_id
                LEFT JOIN sys_user u ON u.id = i.user_id
                """ + where + """
                ORDER BY i.id DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new NotificationAdminInboxItem(
                rs.getLong("inbox_id"),
                rs.getLong("message_id"),
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("notification_type"),
                rs.getString("biz_type"),
                getNullableLong(rs.getObject("biz_id")),
                rs.getString("priority"),
                rs.getString("publish_scope"),
                rs.getString("message_status"),
                rs.getInt("read_status"),
                rs.getString("read_at"),
                rs.getInt("deleted"),
                rs.getString("delivered_at"),
                getNullableLong(rs.getObject("sender_id")),
                rs.getString("message_created_at")
        ), params.toArray());
    }

    public NotificationAdminInboxItem adminInboxDetail(Long inboxId) {
        if (inboxId == null || inboxId <= 0) {
            throw new BizException("inboxId 不能为空");
        }
        List<NotificationAdminInboxItem> items = jdbcTemplate.query("""
                SELECT
                  i.id AS inbox_id,
                  i.message_id,
                  i.user_id,
                  u.username,
                  u.nickname,
                  m.title,
                  m.content,
                  m.notification_type,
                  m.biz_type,
                  m.biz_id,
                  m.priority,
                  m.publish_scope,
                  m.status AS message_status,
                  i.read_status,
                  i.read_at,
                  i.deleted,
                  i.created_at AS delivered_at,
                  m.sender_id,
                  m.created_at AS message_created_at
                FROM notification_inbox i
                JOIN notification_message m ON m.id = i.message_id
                LEFT JOIN sys_user u ON u.id = i.user_id
                WHERE i.id = ?
                LIMIT 1
                """, (rs, rowNum) -> new NotificationAdminInboxItem(
                rs.getLong("inbox_id"),
                rs.getLong("message_id"),
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("notification_type"),
                rs.getString("biz_type"),
                getNullableLong(rs.getObject("biz_id")),
                rs.getString("priority"),
                rs.getString("publish_scope"),
                rs.getString("message_status"),
                rs.getInt("read_status"),
                rs.getString("read_at"),
                rs.getInt("deleted"),
                rs.getString("delivered_at"),
                getNullableLong(rs.getObject("sender_id")),
                rs.getString("message_created_at")
        ), inboxId);
        if (items.isEmpty()) {
            throw new BizException("通知投递记录不存在");
        }
        return items.get(0);
    }

    public NotificationAdminInboxSummaryView adminInboxSummary(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException("userId 不能为空");
        }
        return jdbcTemplate.queryForObject("""
                SELECT
                  u.id AS user_id,
                  u.username,
                  u.nickname,
                  COUNT(i.id) AS total_deliveries,
                  COALESCE(SUM(CASE WHEN i.deleted = 0 AND i.read_status = 0 THEN 1 ELSE 0 END), 0) AS unread_deliveries,
                  COALESCE(SUM(CASE WHEN i.deleted = 0 AND i.read_status = 1 THEN 1 ELSE 0 END), 0) AS read_deliveries,
                  COALESCE(SUM(CASE WHEN i.deleted = 1 THEN 1 ELSE 0 END), 0) AS deleted_deliveries,
                  COALESCE(SUM(CASE WHEN i.deleted = 0 AND m.priority IN ('HIGH', 'URGENT') THEN 1 ELSE 0 END), 0) AS high_priority_deliveries,
                  MAX(i.created_at) AS latest_delivered_at
                FROM sys_user u
                LEFT JOIN notification_inbox i ON i.user_id = u.id
                LEFT JOIN notification_message m ON m.id = i.message_id
                WHERE u.id = ?
                GROUP BY u.id, u.username, u.nickname
                """, (rs, rowNum) -> new NotificationAdminInboxSummaryView(
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getLong("total_deliveries"),
                rs.getLong("unread_deliveries"),
                rs.getLong("read_deliveries"),
                rs.getLong("deleted_deliveries"),
                rs.getLong("high_priority_deliveries"),
                rs.getString("latest_delivered_at")
        ), userId);
    }

    public List<NotificationInboxItem> announcements(Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 100 ? 20 : limit;

        return jdbcTemplate.query("""
                SELECT
                  0 AS inbox_id,
                  m.id AS message_id,
                  0 AS user_id,
                  m.title,
                  m.content,
                  m.notification_type,
                  m.biz_type,
                  m.biz_id,
                  m.priority,
                  0 AS read_status,
                  NULL AS read_at,
                  m.created_at
                FROM notification_message m
                WHERE m.publish_scope = 'ALL'
                  AND m.status = 'PUBLISHED'
                ORDER BY m.id DESC
                LIMIT ?
                """, (rs, rowNum) -> new NotificationInboxItem(
                rs.getLong("inbox_id"),
                rs.getLong("message_id"),
                rs.getLong("user_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("notification_type"),
                rs.getString("biz_type"),
                getNullableLong(rs.getObject("biz_id")),
                rs.getString("priority"),
                rs.getInt("read_status"),
                rs.getString("read_at"),
                rs.getString("created_at")
        ), safeLimit);
    }

    private void deliverToUserIfEnabled(
            Long userId,
            Long messageId,
            NotificationCreateCommand command,
            String notificationType,
            String priority
    ) {
        if (!preferenceService.isEnabled(userId, notificationType)) {
            System.out.println("用户通知偏好已关闭，跳过投递: userId=" + userId + ", type=" + notificationType + ", messageId=" + messageId);
            return;
        }

        insertInbox(messageId, userId);
        pushToUser(userId, messageId, command, notificationType, priority);
    }

    private void pushToUser(
            Long userId,
            Long messageId,
            NotificationCreateCommand command,
            String notificationType,
            String priority
    ) {
        try {
            sseService.sendToUser(userId, new NotificationSseEvent(
                    "NOTIFICATION",
                    messageId,
                    userId,
                    command.title(),
                    command.content(),
                    notificationType,
                    priority,
                    Instant.now().toEpochMilli()
            ));
        } catch (Throwable ex) {
            System.err.println("SSE 推送失败，已降级为仅入库: userId=" + userId + ", messageId=" + messageId + ", error=" + ex.getMessage());
        }
    }

    private Long insertMessage(
            NotificationCreateCommand command,
            String notificationType,
            String priority,
            String scope,
            Long senderId
    ) {
        String sql = """
                INSERT INTO notification_message(
                  title, content, notification_type, biz_type, biz_id,
                  sender_id, publish_scope, priority, status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED')
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, command.title());
            ps.setString(2, command.content());
            ps.setString(3, notificationType);
            ps.setString(4, command.bizType());

            if (command.bizId() == null) {
                ps.setObject(5, null);
            } else {
                ps.setLong(5, command.bizId());
            }

            if (senderId == null) {
                ps.setObject(6, null);
            } else {
                ps.setLong(6, senderId);
            }

            ps.setString(7, scope);
            ps.setString(8, priority);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException("通知创建失败，未获取到 messageId");
        }

        return key.longValue();
    }

    private void insertInbox(Long messageId, Long userId) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO notification_inbox(message_id, user_id) VALUES (?, ?)",
                messageId,
                userId
        );
    }


    private List<Long> normalizeInboxIds(NotificationBatchCommand command) {
        if (command == null || command.inboxIds() == null) {
            return List.of();
        }
        return command.inboxIds().stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(200)
                .toList();
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Long getNullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
