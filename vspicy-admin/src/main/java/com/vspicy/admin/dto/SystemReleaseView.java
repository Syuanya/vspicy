package com.vspicy.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SystemReleaseView(
        Long id,
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
        Long operatorId,
        Long reviewerId,
        LocalDateTime plannedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime rollbackAt,
        String rollbackReason,
        String statusNote,
        Long totalChecks,
        Long passedChecks,
        Long failedChecks,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<SystemReleaseCheckItemView> checks
) {
}
