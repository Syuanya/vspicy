package com.vspicy.profile.dto;

public record HotTagResponse(
        Long tagId,
        String tagName,
        long useCount,
        long userCount,
        double totalScore
) {
}
