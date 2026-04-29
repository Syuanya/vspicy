package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.OperationAuditAlertEventSummaryView;
import com.vspicy.video.dto.OperationAuditAlertEventSyncCommand;
import com.vspicy.video.dto.OperationAuditAlertEventSyncResult;
import com.vspicy.video.dto.OperationAuditAlertEventView;
import com.vspicy.video.service.OperationAuditAlertEventService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos/admin/operation-audit/alert-events")
public class OperationAuditAlertEventController {
    private final OperationAuditAlertEventService alertEventService;

    public OperationAuditAlertEventController(OperationAuditAlertEventService alertEventService) {
        this.alertEventService = alertEventService;
    }

    @GetMapping
    public Result<List<OperationAuditAlertEventView>> list(
            @RequestParam(value = "status", required = false, defaultValue = "OPEN") String status,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "alertType", required = false) String alertType,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(alertEventService.list(status, level, alertType, limit));
    }

    @GetMapping("/summary")
    public Result<OperationAuditAlertEventSummaryView> summary() {
        return Result.ok(alertEventService.summary());
    }

    @PostMapping("/sync")
    public Result<OperationAuditAlertEventSyncResult> sync(
            @RequestBody(required = false) OperationAuditAlertEventSyncCommand command
    ) {
        return Result.ok(alertEventService.sync(command));
    }

    @PostMapping("/{id}/ack")
    public Result<OperationAuditAlertEventView> ack(@PathVariable("id") Long id) {
        return Result.ok(alertEventService.ack(id));
    }

    @PostMapping("/{id}/resolve")
    public Result<OperationAuditAlertEventView> resolve(@PathVariable("id") Long id) {
        return Result.ok(alertEventService.resolve(id));
    }
}
