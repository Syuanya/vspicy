package com.vspicy.video.dto;

import java.util.List;

public record AdminServiceHealthSummaryView(
        String generatedAt,
        String overallStatus,
        Integer upCount,
        Integer warnCount,
        Integer downCount,
        Integer unknownCount,
        List<AdminServiceHealthItemView> items
) {
}
