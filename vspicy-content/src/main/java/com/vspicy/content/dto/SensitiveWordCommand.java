package com.vspicy.content.dto;

public record SensitiveWordCommand(
        String word,
        String category,
        String riskLevel
) {
}
