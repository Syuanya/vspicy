package com.vspicy.interaction.dto;

public record HotContentResponse(
        Long targetId,
        String targetType,
        long viewCount,
        long playCount,
        long likeCount,
        long favoriteCount,
        long commentCount,
        double hotScore
) {
}
