package com.vspicy.recommend.dto;

public record RecommendationFeedbackCommand(
        Long userId,
        Long targetId,
        String targetType,
        String scene,
        String feedbackType,
        String requestId
) {
}
