package com.vspicy.video.dto;

import java.util.List;

public record OperationAuditAlertSummaryView(
        String generatedAt,
        Integer hours,
        Long totalAlerts,
        Long criticalCount,
        Long dangerCount,
        Long warningCount,
        Long infoCount,
        String highestLevel,
        List<OperationAuditAlertView> recentAlerts
) {
}
