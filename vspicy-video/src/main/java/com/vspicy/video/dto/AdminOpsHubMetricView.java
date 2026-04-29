package com.vspicy.video.dto;

public record AdminOpsHubMetricView(
        String groupKey,
        String metricKey,
        String title,
        Long value,
        String level,
        String description,
        String link
) {
}
