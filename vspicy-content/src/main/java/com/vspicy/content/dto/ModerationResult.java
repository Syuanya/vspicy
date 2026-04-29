package com.vspicy.content.dto;

import java.util.List;

public record ModerationResult(
        String result,
        String riskLevel,
        List<String> matchedWords,
        String reason
) {
    public boolean needReview() {
        return !"PASS".equals(result);
    }
}
