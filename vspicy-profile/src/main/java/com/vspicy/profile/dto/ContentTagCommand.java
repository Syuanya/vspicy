package com.vspicy.profile.dto;

import java.util.List;

public record ContentTagCommand(
        Long contentId,
        String contentType,
        List<String> tagNames
) {
}
