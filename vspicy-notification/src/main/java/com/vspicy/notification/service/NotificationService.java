package com.vspicy.notification.service;

import com.vspicy.common.exception.BizException;
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

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Long getNullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
