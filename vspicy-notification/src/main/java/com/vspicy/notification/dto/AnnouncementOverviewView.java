package com.vspicy.notification.dto;

import java.util.List;

public record AnnouncementOverviewView(
        Long totalCount,
        Long draftCount,
        Long publishedCount,
        Long offlineCount,
        Long archivedCount,
        Long pinnedCount,
        Long effectiveCount,
        Long expiredCount,
        Long todayPublishedCount,
        List<AnnouncementMetricItem> categoryDistribution,
        List<AnnouncementMetricItem> statusDistribution
) {
}
