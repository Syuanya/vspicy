package com.vspicy.interaction.dto;

public record InteractionToggleCommand(
        Long userId,
        Long targetId,
        String targetType
) {
}
