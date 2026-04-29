package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.OperationAuditAlertSummaryView;
import com.vspicy.video.dto.OperationAuditAlertView;
import com.vspicy.video.dto.OperationAuditEvidenceView;
import com.vspicy.video.dto.OperationAuditLogView;
import com.vspicy.video.dto.OperationAuditRiskSummaryView;
import com.vspicy.video.service.OperationAuditEnhancedService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/videos/admin/operation-audit")
public class OperationAuditEnhancedController {
    private final OperationAuditEnhancedService enhancedService;

    public OperationAuditEnhancedController(OperationAuditEnhancedService enhancedService) {
        this.enhancedService = enhancedService;
    }

    @GetMapping("/actions")
    public Result<List<String>> actions() {
        return Result.ok(enhancedService.actions());
    }

    @GetMapping("/recent")
    public Result<List<OperationAuditLogView>> recent(
            @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit
    ) {
        return Result.ok(enhancedService.recent(limit));
    }

    @GetMapping("/risk-summary")
    public Result<OperationAuditRiskSummaryView> riskSummary(
            @RequestParam(value = "hours", required = false, defaultValue = "24") Integer hours,
            @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit
    ) {
        return Result.ok(enhancedService.riskSummary(hours, limit));
    }


    @GetMapping("/alerts")
    public Result<List<OperationAuditAlertView>> alerts(
            @RequestParam(value = "hours", required = false, defaultValue = "24") Integer hours,
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit
    ) {
        return Result.ok(enhancedService.alerts(hours, limit));
    }

    @GetMapping("/alerts/summary")
    public Result<OperationAuditAlertSummaryView> alertsSummary(
            @RequestParam(value = "hours", required = false, defaultValue = "24") Integer hours,
            @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit
    ) {
        return Result.ok(enhancedService.alertSummary(hours, limit));
    }

    @GetMapping("/{id}/evidence")
    public Result<OperationAuditEvidenceView> evidence(
            @PathVariable("id") Long id,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        return Result.ok(enhancedService.evidence(id, limit));
    }

    @GetMapping("/list-advanced")
    public Result<List<OperationAuditLogView>> listAdvanced(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "targetId", required = false) String targetId,
            @RequestParam(value = "operatorId", required = false) Long operatorId,
            @RequestParam(value = "requestIp", required = false) String requestIp,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "resultStatus", required = false) String resultStatus,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(enhancedService.listAdvanced(action, targetType, targetId, operatorId, requestIp, startTime, endTime, resultStatus, limit));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> exportCsv(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "targetId", required = false) String targetId,
            @RequestParam(value = "operatorId", required = false) Long operatorId,
            @RequestParam(value = "requestIp", required = false) String requestIp,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "resultStatus", required = false) String resultStatus,
            @RequestParam(value = "limit", required = false, defaultValue = "500") Integer limit
    ) {
        String csv = enhancedService.exportCsv(action, targetType, targetId, operatorId, requestIp, startTime, endTime, resultStatus, limit);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("operation-audit.csv", StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }
}
