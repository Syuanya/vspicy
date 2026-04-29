package com.vspicy.video.dto;

import java.util.List;

public record AdminOpsHubSummaryView(
        String generatedAt,
        List<AdminOpsHubMetricView> metrics,
        List<AdminOpsHubQuickLinkView> quickLinks,
        VideoTranscodeDispatchView dispatchHealth
) {
}
