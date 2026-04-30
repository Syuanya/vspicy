package com.vspicy.admin.dto;

public record OperationLogDailyItem(
        String date,
        Long total,
        Long success,
        Long failed,
        Long slow
) {
}
