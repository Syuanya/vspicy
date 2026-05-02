package com.vspicy.content.dto;

import java.util.List;

public record SensitiveWordTestView(
        String result,
        String riskLevel,
        List<String> matchedWords,
        String reason
) {
}
