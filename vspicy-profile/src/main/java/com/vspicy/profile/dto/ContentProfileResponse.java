package com.vspicy.profile.dto;

import java.util.List;

public record ContentProfileResponse(
        Long contentId,
        String contentType,
        String title,
        String status,
        List<String> tagNames,
        double hotScore,
        double qualityScore
) {
}
