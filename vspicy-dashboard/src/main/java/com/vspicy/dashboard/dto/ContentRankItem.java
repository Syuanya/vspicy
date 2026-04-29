package com.vspicy.dashboard.dto;

public record ContentRankItem(
        Long targetId,
        String targetType,
        String title,
        String status,
        long viewCount,
        long playCount,
        long likeCount,
        long favoriteCount,
        long commentCount,
        double hotScore
) {
}
