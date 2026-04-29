package com.vspicy.dashboard.dto;

public record MetricCard(
        String key,
        String name,
        long value,
        String unit,
        String description
) {
}
