package com.vspicy.notification.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.notification.dto.NotificationPreferenceItem;
import com.vspicy.notification.dto.NotificationPreferenceSaveCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationPreferenceService {
    private static final String SECURITY = "SECURITY";

    private final JdbcTemplate jdbcTemplate;

    public NotificationPreferenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<NotificationPreferenceItem> list(Long userId) {
        if (userId == null) {
            throw new BizException("未登录");
        }
        Map<String, NotificationPreferenceItem> defaults = defaults();

        List<NotificationPreferenceItem> saved = jdbcTemplate.query("""
                SELECT notification_type, enabled
                FROM notification_preference
                WHERE user_id = ?
                """, (rs, rowNum) -> {
            String type = rs.getString("notification_type");
            return new NotificationPreferenceItem(
                    type,
                    nameOf(type),
                    rs.getInt("enabled") == 1 || SECURITY.equals(type),
                    SECURITY.equals(type)
            );
        }, userId);

        for (NotificationPreferenceItem item : saved) {
            defaults.put(item.notificationType(), item);
        }

        // SECURITY 后端强制开启，避免历史脏数据影响安全通知。
        defaults.put(SECURITY, new NotificationPreferenceItem(SECURITY, nameOf(SECURITY), true, true));
        return defaults.values().stream().toList();
    }

    @Transactional
    public List<NotificationPreferenceItem> save(NotificationPreferenceSaveCommand command, Long userId) {
        if (userId == null) {
            throw new BizException("未登录");
        }

        if (command == null || command.preferences() == null) {
            throw new BizException("preferences 不能为空");
        }

        for (NotificationPreferenceItem item : command.preferences()) {
            if (item == null || item.notificationType() == null || item.notificationType().isBlank()) {
                continue;
            }

            String type = item.notificationType().trim().toUpperCase();
            int enabled = SECURITY.equals(type) ? 1 : Boolean.TRUE.equals(item.enabled()) ? 1 : 0;

            jdbcTemplate.update("""
                    INSERT INTO notification_preference(user_id, notification_type, enabled)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      enabled = VALUES(enabled)
                    """, userId, type, enabled);
        }

        return list(userId);
    }

    public boolean isEnabled(Long userId, String notificationType) {
        if (userId == null) {
            return true;
        }

        String type = notificationType == null || notificationType.isBlank()
                ? "SYSTEM"
                : notificationType.trim().toUpperCase();

        if (SECURITY.equals(type)) {
            return true;
        }

        List<Integer> values = jdbcTemplate.query("""
                SELECT enabled
                FROM notification_preference
                WHERE user_id = ?
                  AND notification_type = ?
                LIMIT 1
                """, (rs, rowNum) -> rs.getInt("enabled"), userId, type);

        if (values.isEmpty()) {
            return true;
        }

        return values.get(0) == 1;
    }

    private Map<String, NotificationPreferenceItem> defaults() {
        Map<String, NotificationPreferenceItem> map = new LinkedHashMap<>();
        map.put("SYSTEM", new NotificationPreferenceItem("SYSTEM", nameOf("SYSTEM"), true, false));
        map.put("AUDIT", new NotificationPreferenceItem("AUDIT", nameOf("AUDIT"), true, false));
        map.put("TRANSCODE", new NotificationPreferenceItem("TRANSCODE", nameOf("TRANSCODE"), true, false));
        map.put("INTERACTION", new NotificationPreferenceItem("INTERACTION", nameOf("INTERACTION"), true, false));
        map.put("SECURITY", new NotificationPreferenceItem("SECURITY", nameOf("SECURITY"), true, true));
        return map;
    }

    private String nameOf(String type) {
        return switch (type) {
            case "SYSTEM" -> "系统通知";
            case "AUDIT" -> "审核通知";
            case "TRANSCODE" -> "转码通知";
            case "INTERACTION" -> "互动通知";
            case "SECURITY" -> "安全通知";
            default -> type;
        };
    }
}
