package com.vspicy.admin.controller;

import com.vspicy.admin.dto.OperationAuditCleanupCommand;
import com.vspicy.admin.dto.OperationAuditCleanupPreviewView;
import com.vspicy.admin.dto.OperationAuditCreateCommand;
import com.vspicy.admin.dto.OperationAuditHandleCommand;
import com.vspicy.admin.dto.OperationAuditOverviewView;
import com.vspicy.admin.dto.OperationAuditView;
import com.vspicy.admin.service.OperationAuditService;
import com.vspicy.common.core.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统操作审计中心。
 */
@RestController
@RequestMapping("/api/admin/operation-audit-logs")
public class AdminOperationAuditController {
    private final OperationAuditService operationAuditService;

    public AdminOperationAuditController(OperationAuditService operationAuditService) {
        this.operationAuditService = operationAuditService;
    }

    @GetMapping("/overview")
    public Result<OperationAuditOverviewView> overview() {
        return Result.ok(operationAuditService.overview());
    }

    @GetMapping
    public Result<List<OperationAuditView>> list(
            @RequestParam(value = "serviceName", required = false) String serviceName,
            @RequestParam(value = "actionType", required = false) String actionType,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            @RequestParam(value = "handleStatus", required = false) String handleStatus,
            @RequestParam(value = "success", required = false) Boolean success,
            @RequestParam(value = "operatorId", required = false) Long operatorId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(operationAuditService.list(serviceName, actionType, riskLevel, handleStatus, success, operatorId, keyword, limit));
    }

    @GetMapping("/{id}")
    public Result<OperationAuditView> get(@PathVariable("id") Long id) {
        return Result.ok(operationAuditService.get(id));
    }

    @PostMapping
    public Result<OperationAuditView> create(@RequestBody OperationAuditCreateCommand command) {
        return Result.ok(operationAuditService.create(command));
    }

    @PostMapping("/{id}/review")
    public Result<OperationAuditView> review(
            @PathVariable("id") Long id,
            @RequestBody(required = false) OperationAuditHandleCommand command,
            HttpServletRequest request
    ) {
        String remark = command == null ? null : command.handleRemark();
        return Result.ok(operationAuditService.review(id, remark, operatorId(request)));
    }

    @PostMapping("/{id}/ignore")
    public Result<OperationAuditView> ignore(
            @PathVariable("id") Long id,
            @RequestBody(required = false) OperationAuditHandleCommand command,
            HttpServletRequest request
    ) {
        String remark = command == null ? null : command.handleRemark();
        return Result.ok(operationAuditService.ignore(id, remark, operatorId(request)));
    }

    @PostMapping("/{id}/reopen")
    public Result<OperationAuditView> reopen(@PathVariable("id") Long id) {
        return Result.ok(operationAuditService.reopen(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        operationAuditService.delete(id);
        return Result.ok();
    }

    @GetMapping("/cleanup-preview")
    public Result<OperationAuditCleanupPreviewView> cleanupPreview(
            @RequestParam(value = "beforeDays", required = false, defaultValue = "30") Integer beforeDays,
            @RequestParam(value = "onlyHandled", required = false, defaultValue = "true") Boolean onlyHandled
    ) {
        return Result.ok(operationAuditService.cleanupPreview(new OperationAuditCleanupCommand(beforeDays, onlyHandled)));
    }

    @PostMapping("/cleanup")
    public Result<Long> cleanup(@RequestBody OperationAuditCleanupCommand command) {
        return Result.ok(operationAuditService.cleanup(command));
    }

    private Long operatorId(HttpServletRequest request) {
        String value = firstHeader(request, "X-User-Id", "x-user-id", "X-UserId", "x-userid");
        if (value == null || value.isBlank()) {
            return 1L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 1L;
        }
    }

    private String firstHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
