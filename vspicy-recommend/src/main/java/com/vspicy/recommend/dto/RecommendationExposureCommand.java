package com.vspicy.recommend.dto;

public record RecommendationExposureCommand(
        Long userId,
        Long targetId,
        String targetType,
        String scene,
        Integer rankNo,
        Double score,
        String requestId
) {
}
