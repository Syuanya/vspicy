package com.vspicy.admin.dto;

public record SystemReleaseCommand(
        String releaseNo,
        String versionName,
        String environment,
        String status,
        String riskLevel,
        String title,
        String description,
        String services,
        String gitBranch,
        String gitCommit,
        String imageTag,
        String releaseNote,
        String plannedAt,
        String statusNote,
        String rollbackReason,
        Long reviewerId
) {
}
