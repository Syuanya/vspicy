package com.vspicy.admin.controller;

import com.vspicy.admin.audit.AuditLog;
import com.vspicy.admin.dto.OperationLogCleanupCommand;
import com.vspicy.admin.dto.OperationLogCleanupView;
import com.vspicy.admin.dto.OperationLogOverviewView;
import com.vspicy.admin.entity.OperationLog;
import com.vspicy.admin.service.OperationLogService;
import com.vspicy.common.core.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/operation-logs")
public class OperationLogController {
    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public Result<List<OperationLog>> list(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "operationType", required = false) String operationType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(operationLogService.list(userId, operationType, status, keyword, startTime, endTime, limit));
    }

    @GetMapping("/overview")
    public Result<OperationLogOverviewView> overview(
            @RequestParam(value = "days", required = false, defaultValue = "7") Integer days
    ) {
        return Result.ok(operationLogService.overview(days));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "operationType", required = false) String operationType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            HttpServletResponse response
    ) throws IOException {
        byte[] content = operationLogService.exportCsv(userId, operationType, status, keyword, startTime, endTime);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=operation-logs.csv");
        response.getOutputStream().write(content);
    }

    @PostMapping("/cleanup")
    @AuditLog(type = "DELETE", title = "清理操作审计日志")
    public Result<OperationLogCleanupView> cleanup(@RequestBody OperationLogCleanupCommand command) {
        return Result.ok(operationLogService.cleanup(command));
    }
}
