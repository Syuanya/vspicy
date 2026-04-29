package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.OperationAuditAlertEventAutomationStatusView;
import com.vspicy.video.dto.OperationAuditAlertEventCleanupCommand;
import com.vspicy.video.dto.OperationAuditAlertEventCleanupResult;
import com.vspicy.video.dto.OperationAuditAlertEventSyncCommand;
import com.vspicy.video.scheduler.OperationAuditAlertEventAutoSyncScheduler;
import com.vspicy.video.service.OperationAuditAlertEventMaintenanceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/admin/operation-audit/alert-events/automation")
public class OperationAuditAlertEventAutomationController {
    private final OperationAuditAlertEventAutoSyncScheduler scheduler;
    private final OperationAuditAlertEventMaintenanceService maintenanceService;

    public OperationAuditAlertEventAutomationController(
            OperationAuditAlertEventAutoSyncScheduler scheduler,
            OperationAuditAlertEventMaintenanceService maintenanceService
    ) {
        this.scheduler = scheduler;
        this.maintenanceService = maintenanceService;
    }

    @GetMapping("/status")
    public Result<OperationAuditAlertEventAutomationStatusView> status() {
        return Result.ok(scheduler.status());
    }

    @PostMapping("/sync-once")
    public Result<OperationAuditAlertEventAutomationStatusView> syncOnce(
            @RequestBody(required = false) OperationAuditAlertEventSyncCommand command
    ) {
        Integer hours = command == null ? null : command.hours();
        Integer limit = command == null ? null : command.limit();
        return Result.ok(scheduler.syncOnce(hours, limit));
    }

    @PostMapping("/cleanup-resolved")
    public Result<OperationAuditAlertEventCleanupResult> cleanupResolved(
            @RequestBody(required = false) OperationAuditAlertEventCleanupCommand command
    ) {
        return Result.ok(maintenanceService.cleanupResolved(command));
    }
}
