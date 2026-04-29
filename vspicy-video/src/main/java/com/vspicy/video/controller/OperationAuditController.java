package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.OperationAuditCommand;
import com.vspicy.video.dto.OperationAuditLogView;
import com.vspicy.video.dto.OperationAuditStatsView;
import com.vspicy.video.service.OperationAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos/admin/operation-audit")
public class OperationAuditController {
    private final OperationAuditService auditService;

    public OperationAuditController(OperationAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/list")
    public Result<List<OperationAuditLogView>> list(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "operatorId", required = false) Long operatorId,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(auditService.list(action, targetType, operatorId, limit));
    }

    @GetMapping("/stats")
    public Result<OperationAuditStatsView> stats() {
        return Result.ok(auditService.stats());
    }

    @PostMapping("/record")
    public Result<OperationAuditLogView> record(
            @RequestBody OperationAuditCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(auditService.record(command, request));
    }
}
