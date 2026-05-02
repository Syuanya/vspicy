package com.vspicy.admin.dto;

import java.util.List;

public record SupportTicketOverviewView(
        long totalCount,
        long openCount,
        long processingCount,
        long resolvedCount,
        long closedCount,
        long urgentCount,
        long todayCreatedCount,
        long unassignedCount,
        List<SupportTicketMetricItem> categoryDistribution,
        List<SupportTicketMetricItem> priorityDistribution,
        List<SupportTicketMetricItem> statusDistribution
) {
}
