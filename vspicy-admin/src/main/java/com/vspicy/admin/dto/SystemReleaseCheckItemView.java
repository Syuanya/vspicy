package com.vspicy.admin.dto;

import java.time.LocalDateTime;

public record SystemReleaseCheckItemView(
        Long id,
        Long releaseId,
        String checkName,
        String checkType,
        String status,
        String resultNote,
        Integer sortNo,
        Long checkedBy,
        LocalDateTime checkedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
