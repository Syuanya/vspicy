package com.vspicy.content.dto;

public record AuditReviewCommand(
        Long reviewerId,
        String reason
) {
}
