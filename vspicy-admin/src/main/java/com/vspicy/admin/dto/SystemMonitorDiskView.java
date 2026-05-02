package com.vspicy.admin.dto;

public record SystemMonitorDiskView(
        String path,
        long total,
        long free,
        long usable,
        long used,
        double usageRate,
        String totalText,
        String freeText,
        String usableText,
        String usedText
) {
}
