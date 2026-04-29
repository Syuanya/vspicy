package com.vspicy.video.dto;

import java.util.List;
import java.util.Map;

public record ConfigDiagnosticView(
        List<String> activeProfiles,
        Map<String, String> datasource,
        Map<String, String> redis,
        Map<String, String> minio,
        Map<String, String> rocketmq,
        Map<String, String> videoStorage,
        Map<String, String> diagnostics
) {
}
