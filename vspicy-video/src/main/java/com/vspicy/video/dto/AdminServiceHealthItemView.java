package com.vspicy.video.dto;

import java.util.Map;

public record AdminServiceHealthItemView(
        String key,
        String title,
        String groupKey,
        String status,
        String level,
        String message,
        String suggestion,
        Long durationMs,
        Map<String, String> details
) {
}
