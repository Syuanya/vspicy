package com.vspicy.notification.service;

import com.vspicy.notification.dto.NotificationDailyStatItem;
import com.vspicy.notification.dto.NotificationMetricItem;
import com.vspicy.notification.dto.NotificationOverviewView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationOverviewService {
    private final JdbcTemplate jdbcTemplate;
    private final NotificationSseService sseService;

    public NotificationOverviewService(JdbcTemplate jdbcTemplate, NotificationSseService sseService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sseService = sseService;
    }

    public NotificationOverviewView overview(Integer days) {
        int safeDays = days == null || days <= 0 || days > 90 ? 7 : days;

        long totalMessages = count("SELECT COUNT(*) FROM notification_message WHERE status = 'PUBLISHED'");
        long totalDeliveries = count("SELECT COUNT(*) FROM notification_inbox WHERE deleted = 0");
        long unreadDeliveries = count("SELECT COUNT(*) FROM notification_inbox WHERE deleted = 0 AND read_status = 0");
        long readDeliveries = count("SELECT COUNT(*) FROM notification_inbox WHERE deleted = 0 AND read_status = 1");
        double readRate = totalDeliveries == 0 ? 0 : Math.round((readDeliveries * 10000.0 / totalDeliveries)) / 100.0;

        long todayMessages = count("SELECT COUNT(*) FROM notification_message WHERE status = 'PUBLISHED' AND created_at >= CURDATE()");
        long todayDeliveries = count("SELECT COUNT(*) FROM notification_inbox WHERE deleted = 0 AND created_at >= CURDATE()");
        long todayUnreadDeliveries = count("SELECT COUNT(*) FROM notification_inbox WHERE deleted = 0 AND read_status = 0 AND created_at >= CURDATE()");
        long failedEvents = count("SELECT COUNT(*) FROM notification_event_log WHERE status IN ('FAILED', 'DEAD')");
        long pendingEvents = count("SELECT COUNT(*) FROM notification_event_log WHERE status IN ('PENDING', 'SENT')");
        long enabledTemplates = safeCount("SELECT COUNT(*) FROM notification_template WHERE enabled = 1 AND deleted = 0");
        long disabledTemplates = safeCount("SELECT COUNT(*) FROM notification_template WHERE enabled = 0 AND deleted = 0");

        return new NotificationOverviewView(
                totalMessages,
                totalDeliveries,
                unreadDeliveries,
                readDeliveries,
                readRate,
                todayMessages,
                todayDeliveries,
                todayUnreadDeliveries,
                failedEvents,
                pendingEvents,
                enabledTemplates,
                disabledTemplates,
                sseService.onlineUserCount(),
                sseService.onlineConnectionCount(),
                messageMetricStats("notification_type", safeDays),
                messageMetricStats("priority", safeDays),
                eventStatusStats(safeDays),
                dailyStats(safeDays)
        );
    }

    private List<NotificationMetricItem> messageMetricStats(String column, int days) {
        String safeColumn = "priority".equals(column) ? "priority" : "notification_type";
        String sql = """
                SELECT %s AS name, COUNT(*) AS value
                FROM notification_message
                WHERE status = 'PUBLISHED'
                  AND created_at >= DATE_SUB(CURDATE(), INTERVAL %d DAY)
                GROUP BY %s
                ORDER BY value DESC
                LIMIT 20
                """.formatted(safeColumn, days - 1, safeColumn);

        return jdbcTemplate.query(sql, (rs, rowNum) -> new NotificationMetricItem(
                rs.getString("name"),
                rs.getLong("value")
        ));
    }

    private List<NotificationMetricItem> eventStatusStats(int days) {
        String sql = """
                SELECT status AS name, COUNT(*) AS value
                FROM notification_event_log
                WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL %d DAY)
                GROUP BY status
                ORDER BY value DESC
                """.formatted(days - 1);

        return jdbcTemplate.query(sql, (rs, rowNum) -> new NotificationMetricItem(
                rs.getString("name"),
                rs.getLong("value")
        ));
    }

    private List<NotificationDailyStatItem> dailyStats(int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        Map<String, DailyAccumulator> dailyMap = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            dailyMap.put(start.plusDays(i).toString(), new DailyAccumulator());
        }

        String messageSql = """
                SELECT DATE(created_at) AS day, COUNT(*) AS value
                FROM notification_message
                WHERE status = 'PUBLISHED'
                  AND created_at >= DATE_SUB(CURDATE(), INTERVAL %d DAY)
                GROUP BY DATE(created_at)
                ORDER BY day ASC
                """.formatted(days - 1);
        jdbcTemplate.query(messageSql, rs -> {
            DailyAccumulator accumulator = dailyMap.get(rs.getString("day"));
            if (accumulator != null) {
                accumulator.messageCount = rs.getLong("value");
            }
        });

        String inboxSql = """
                SELECT DATE(created_at) AS day,
                       COUNT(*) AS delivery_count,
                       SUM(CASE WHEN read_status = 1 THEN 1 ELSE 0 END) AS read_count,
                       SUM(CASE WHEN read_status = 0 THEN 1 ELSE 0 END) AS unread_count
                FROM notification_inbox
                WHERE deleted = 0
                  AND created_at >= DATE_SUB(CURDATE(), INTERVAL %d DAY)
                GROUP BY DATE(created_at)
                ORDER BY day ASC
                """.formatted(days - 1);
        jdbcTemplate.query(inboxSql, rs -> {
            DailyAccumulator accumulator = dailyMap.get(rs.getString("day"));
            if (accumulator != null) {
                accumulator.deliveryCount = rs.getLong("delivery_count");
                accumulator.readCount = rs.getLong("read_count");
                accumulator.unreadCount = rs.getLong("unread_count");
            }
        });

        List<NotificationDailyStatItem> result = new ArrayList<>();
        dailyMap.forEach((day, accumulator) -> result.add(new NotificationDailyStatItem(
                day,
                accumulator.messageCount,
                accumulator.deliveryCount,
                accumulator.readCount,
                accumulator.unreadCount
        )));
        return result;
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private long safeCount(String sql) {
        try {
            return count(sql);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static class DailyAccumulator {
        private long messageCount;
        private long deliveryCount;
        private long readCount;
        private long unreadCount;
    }
}
