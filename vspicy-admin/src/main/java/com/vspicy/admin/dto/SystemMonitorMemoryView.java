package com.vspicy.admin.dto;

public record SystemMonitorMemoryView(
        long used,
        long committed,
        long max,
        double usageRate,
        String usedText,
        String committedText,
        String maxText
) {
}
