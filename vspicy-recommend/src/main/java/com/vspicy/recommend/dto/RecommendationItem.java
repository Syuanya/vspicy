package com.vspicy.recommend.dto;

public record RecommendationItem(
        Long targetId,
        String targetType,
        String title,
        String status,
        double score,
        long viewCount,
        long playCount,
        long likeCount,
        long favoriteCount,
        long commentCount,
        String reason,
        String recallSource
) {
}
