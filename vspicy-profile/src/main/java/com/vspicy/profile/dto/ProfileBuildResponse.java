package com.vspicy.profile.dto;

public record ProfileBuildResponse(
        Long userId,
        int behaviorCount,
        int tagCount,
        String message
) {
}
