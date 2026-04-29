package com.vspicy.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.notification.dto.NotificationEventLogItem;
import com.vspicy.notification.dto.NotificationMqEventMessage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationEventLogService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NotificationEventLogService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean createPending(NotificationMqEventMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            jdbcTemplate.update("""
                    INSERT INTO notification_event_log(
                      event_id, event_type, receiver_user_id, biz_id, status, payload_json
                    )
                    VALUES (?, ?, ?, ?, 'PENDING', ?)
                    """,
                    message.eventId(),
                    message.eventType(),
                    message.receiverUserId(),
                    message.bizId(),
                    payload
            );
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        } catch (Exception ex) {
            throw new BizException("记录通知事件失败：" + ex.getMessage());
        }
    }

    public boolean isSuccess(String eventId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM notification_event_log
                WHERE event_id = ?
                  AND status = 'SUCCESS'
                """, Long.class, eventId);
        return count != null && count > 0;
    }

    public void markSent(String eventId) {
        jdbcTemplate.update("""
                UPDATE notification_event_log
                SET status = 'SENT',
                    error_message = NULL
                WHERE event_id = ?
                  AND status IN ('PENDING', 'FAILED')
                """, eventId);
    }

    public void markSuccess(String eventId, Long messageId) {
        jdbcTemplate.update("""
                UPDATE notification_event_log
                SET status = 'SUCCESS',
                    message_id = ?,
                    error_message = NULL
                WHERE event_id = ?
                """, messageId, eventId);
    }

    public void markSkipped(String eventId, String reason) {
        jdbcTemplate.update("""
                UPDATE notification_event_log
                SET status = 'SKIPPED',
                    error_message = ?
                WHERE event_id = ?
                  AND status <> 'SUCCESS'
                """, limit(reason, 2000), eventId);
    }

    public void markFailed(String eventId, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE notification_event_log
                SET status = 'FAILED',
                    retry_count = retry_count + 1,
                    last_retry_at = NOW(),
                    error_message = ?
                WHERE event_id = ?
                """, limit(errorMessage, 2000), eventId);
    }

    public void markDead(String eventId, String reason) {
        jdbcTemplate.update("""
                UPDATE notification_event_log
                SET status = 'DEAD',
                    dead_at = NOW(),
                    error_message = ?
                WHERE event_id = ?
                  AND status = 'FAILED'
                """, limit(reason, 2000), eventId);
    }

    public NotificationMqEventMessage loadMessage(String eventId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT payload_json FROM notification_event_log WHERE event_id = ? LIMIT 1",
                (rs, rowNum) -> rs.getString("payload_json"),
                eventId
        );

        if (rows.isEmpty()) {
            throw new BizException("事件不存在：" + eventId);
        }

        try {
            return objectMapper.readValue(rows.get(0), NotificationMqEventMessage.class);
        } catch (Exception ex) {
            throw new BizException("事件 payload 解析失败：" + ex.getMessage());
        }
    }

    public List<String> retryableFailedEventIds(int maxRetryCount, int limit) {
        int safeLimit = limit <= 0 || limit > 500 ? 20 : limit;
        return jdbcTemplate.query("""
                SELECT event_id
                FROM notification_event_log
                WHERE status = 'FAILED'
                  AND retry_count < ?
                ORDER BY updated_at ASC, id ASC
                LIMIT ?
                """, (rs, rowNum) -> rs.getString("event_id"), maxRetryCount, safeLimit);
    }

    public List<String> deadCandidateEventIds(int maxRetryCount, int limit) {
        int safeLimit = limit <= 0 || limit > 500 ? 100 : limit;
        return jdbcTemplate.query("""
                SELECT event_id
                FROM notification_event_log
                WHERE status = 'FAILED'
                  AND retry_count >= ?
                ORDER BY updated_at ASC, id ASC
                LIMIT ?
                """, (rs, rowNum) -> rs.getString("event_id"), maxRetryCount, safeLimit);
    }

    public List<NotificationEventLogItem> list(String status, String eventType, Long receiverUserId, Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 100 : limit;

        StringBuilder sql = new StringBuilder("""
                SELECT id, event_id, event_type, receiver_user_id, biz_id, message_id,
                       status, retry_count, error_message, created_at, updated_at
                FROM notification_event_log
                WHERE 1 = 1
                """);

        java.util.ArrayList<Object> params = new java.util.ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ? ");
            params.add(status);
        }
        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND event_type = ? ");
            params.add(eventType);
        }
        if (receiverUserId != null) {
            sql.append(" AND receiver_user_id = ? ");
            params.add(receiverUserId);
        }

        sql.append(" ORDER BY id DESC LIMIT ? ");
        params.add(safeLimit);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new NotificationEventLogItem(
                rs.getLong("id"),
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getLong("receiver_user_id"),
                getNullableLong(rs.getObject("biz_id")),
                getNullableLong(rs.getObject("message_id")),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getString("error_message"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        ), params.toArray());
    }

    private Long getNullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
