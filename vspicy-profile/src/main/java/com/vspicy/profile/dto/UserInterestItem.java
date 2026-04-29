package com.vspicy.profile.dto;

public record UserInterestItem(
        Long userId,
        Long tagId,
        String tagName,
        double score,
        String source,
        String lastBehaviorAt
) {
}
