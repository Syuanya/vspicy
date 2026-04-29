package com.vspicy.interaction.dto;

public record InteractionStatusResponse(
        Long userId,
        Long targetId,
        String targetType,
        boolean liked,
        boolean favorited,
        long likeCount,
        long favoriteCount,
        long commentCount
) {
}
