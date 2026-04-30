package com.vspicy.notification.dto;

import java.util.List;

public record NotificationOverviewView(
        Long totalMessages,
        Long totalDeliveries,
        Long unreadDeliveries,
        Long readDeliveries,
        Double readRate,
        Long todayMessages,
        Long todayDeliveries,
        Long todayUnreadDeliveries,
        Long failedEvents,
        Long pendingEvents,
        Long enabledTemplates,
        Long disabledTemplates,
        Integer onlineUsers,
        Integer onlineConnections,
        List<NotificationMetricItem> typeStats,
        List<NotificationMetricItem> priorityStats,
        List<NotificationMetricItem> eventStatusStats,
        List<NotificationDailyStatItem> dailyStats
) {
}
