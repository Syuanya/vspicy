package com.vspicy.admin.dto;

public record ExceptionLogDailyItem(
        String date,
        Long total,
        Long newCount,
        Long resolvedCount,
        Long criticalCount
) {
}
