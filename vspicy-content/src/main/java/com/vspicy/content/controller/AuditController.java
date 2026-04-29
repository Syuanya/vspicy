package com.vspicy.content.controller;

import com.vspicy.common.core.Result;
import com.vspicy.content.dto.AuditReviewCommand;
import com.vspicy.content.entity.AuditTask;
import com.vspicy.content.service.AuditService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/tasks")
    public Result<List<AuditTask>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(auditService.list(status, bizType, limit));
    }

    @PostMapping("/tasks/{taskId}/pass")
    public Result<AuditTask> pass(
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) AuditReviewCommand command
    ) {
        return Result.ok(auditService.pass(taskId, command));
    }

    @PostMapping("/tasks/{taskId}/reject")
    public Result<AuditTask> reject(
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) AuditReviewCommand command
    ) {
        return Result.ok(auditService.reject(taskId, command));
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("vspicy-content audit ok");
    }
}
