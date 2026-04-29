package com.vspicy.interaction.dto;

public record BehaviorLogCommand(
        Long userId,
        Long targetId,
        String targetType,
        String actionType,
        Integer durationSeconds,
        String extraJson
) {
}
