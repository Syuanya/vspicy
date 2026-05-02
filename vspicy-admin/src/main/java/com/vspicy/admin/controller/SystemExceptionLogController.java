package com.vspicy.admin.controller;

import com.vspicy.admin.audit.AuditLog;
import com.vspicy.admin.dto.ExceptionLogCleanupCommand;
import com.vspicy.admin.dto.ExceptionLogCleanupView;
import com.vspicy.admin.dto.ExceptionLogOverviewView;
import com.vspicy.admin.dto.ExceptionLogResolveCommand;
import com.vspicy.admin.dto.ExceptionLogView;
import com.vspicy.admin.service.SystemExceptionLogService;
import com.vspicy.common.core.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/exception-logs")
public class SystemExceptionLogController {
    private final SystemExceptionLogService exceptionLogService;

    public SystemExceptionLogController(SystemExceptionLogService exceptionLogService) {
        this.exceptionLogService = exceptionLogService;
    }

    @GetMapping("/overview")
    public Result<ExceptionLogOverviewView> overview(
            @RequestParam(value = "days", required = false, defaultValue = "7") Integer days
    ) {
        return Result.ok(exceptionLogService.overview(days));
    }

    @GetMapping
    public Result<List<ExceptionLogView>> list(
            @RequestParam(value = "serviceName", required = false) String serviceName,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(exceptionLogService.list(serviceName, severity, status, keyword, startTime, endTime, limit));
    }

    @GetMapping("/{id}")
    public Result<ExceptionLogView> get(@PathVariable("id") Long id) {
        return Result.ok(exceptionLogService.get(id));
    }

    @AuditLog(type = "UPDATE", title = "处理异常日志")
    @PostMapping("/{id}/resolve")
    public Result<ExceptionLogView> resolve(@PathVariable("id") Long id,
                                            @RequestBody(required = false) ExceptionLogResolveCommand command,
                                            HttpServletRequest request) {
        return Result.ok(exceptionLogService.resolve(id, command, currentUserId(request)));
    }

    @AuditLog(type = "UPDATE", title = "忽略异常日志")
    @PostMapping("/{id}/ignore")
    public Result<ExceptionLogView> ignore(@PathVariable("id") Long id,
                                           @RequestBody(required = false) ExceptionLogResolveCommand command,
                                           HttpServletRequest request) {
        return Result.ok(exceptionLogService.ignore(id, command, currentUserId(request)));
    }

    @AuditLog(type = "UPDATE", title = "重新打开异常日志")
    @PostMapping("/{id}/reopen")
    public Result<ExceptionLogView> reopen(@PathVariable("id") Long id) {
        return Result.ok(exceptionLogService.reopen(id));
    }

    @AuditLog(type = "DELETE", title = "删除异常日志")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        exceptionLogService.delete(id);
        return Result.ok();
    }

    @AuditLog(type = "DELETE", title = "清理异常日志")
    @PostMapping("/cleanup")
    public Result<ExceptionLogCleanupView> cleanup(@RequestBody(required = false) ExceptionLogCleanupCommand command) {
        return Result.ok(exceptionLogService.cleanup(command));
    }

    private Long currentUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader("X-User-Id");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}
