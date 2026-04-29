package com.vspicy.video.dto;

import java.util.List;

public record OperationAuditEvidenceView(
        OperationAuditLogView current,
        List<OperationAuditLogView> targetTimeline,
        List<OperationAuditLogView> sameOperatorRecent,
        List<OperationAuditLogView> sameIpRecent,
        List<String> riskHints
) {
}
