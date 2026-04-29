package com.vspicy.video.dto;

public record AdminOpsHubQuickLinkView(
        String title,
        String description,
        String link,
        String level,
        String permissionCode
) {
}
