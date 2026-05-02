package com.vspicy.admin.controller;

import com.vspicy.admin.dto.SystemConfigBatchUpdateCommand;
import com.vspicy.admin.dto.SystemConfigChangeLogView;
import com.vspicy.admin.dto.SystemConfigGroupView;
import com.vspicy.admin.dto.SystemConfigOverviewView;
import com.vspicy.admin.dto.SystemConfigSaveCommand;
import com.vspicy.admin.dto.SystemConfigView;
import com.vspicy.admin.service.SystemConfigService;
import com.vspicy.common.core.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置中心。
 */
@RestController
@RequestMapping("/api/admin/system-configs")
public class AdminSystemConfigController {
    private final SystemConfigService systemConfigService;

    public AdminSystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping("/overview")
    public Result<SystemConfigOverviewView> overview() {
        return Result.ok(systemConfigService.overview());
    }

    @GetMapping("/groups")
    public Result<List<SystemConfigGroupView>> groups() {
        return Result.ok(systemConfigService.groups());
    }

    @GetMapping
    public Result<List<SystemConfigView>> list(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String valueType,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Boolean editable,
        @RequestParam(required = false) Boolean sensitive,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false, defaultValue = "false") Boolean includeSensitive,
        @RequestParam(required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(systemConfigService.list(
            category,
            valueType,
            status,
            editable,
            sensitive,
            keyword,
            includeSensitive,
            limit
        ));
    }

    @GetMapping("/{id}")
    public Result<SystemConfigView> get(
            @PathVariable("id") Long id,
            @RequestParam(value = "includeSensitive", required = false, defaultValue = "false") Boolean includeSensitive
    ) {
        return Result.ok(systemConfigService.get(id, includeSensitive));
    }

    @GetMapping("/key/{configKey}")
    public Result<SystemConfigView> getByKey(
            @PathVariable("configKey") String configKey,
            @RequestParam(value = "includeSensitive", required = false, defaultValue = "false") Boolean includeSensitive
    ) {
        return Result.ok(systemConfigService.getByKey(configKey, includeSensitive));
    }

    @PostMapping
    public Result<SystemConfigView> create(@RequestBody SystemConfigSaveCommand command, HttpServletRequest request) {
        return Result.ok(systemConfigService.create(command, operatorId(request), operatorName(request), clientIp(request)));
    }

    @PutMapping("/{id}")
    public Result<SystemConfigView> update(@PathVariable("id") Long id, @RequestBody SystemConfigSaveCommand command, HttpServletRequest request) {
        return Result.ok(systemConfigService.update(id, command, operatorId(request), operatorName(request), clientIp(request)));
    }

    @PostMapping("/batch-update")
    public Result<List<SystemConfigView>> batchUpdate(@RequestBody SystemConfigBatchUpdateCommand command, HttpServletRequest request) {
        return Result.ok(systemConfigService.batchUpdate(command, operatorId(request), operatorName(request), clientIp(request)));
    }

    @PostMapping("/{id}/enable")
    public Result<SystemConfigView> enable(@PathVariable("id") Long id, @RequestBody(required = false) SystemConfigSaveCommand command, HttpServletRequest request) {
        String reason = command == null ? null : command.changeReason();
        return Result.ok(systemConfigService.changeStatus(id, "ENABLED", reason, operatorId(request), operatorName(request), clientIp(request)));
    }

    @PostMapping("/{id}/disable")
    public Result<SystemConfigView> disable(@PathVariable("id") Long id, @RequestBody(required = false) SystemConfigSaveCommand command, HttpServletRequest request) {
        String reason = command == null ? null : command.changeReason();
        return Result.ok(systemConfigService.changeStatus(id, "DISABLED", reason, operatorId(request), operatorName(request), clientIp(request)));
    }

    @PostMapping("/{id}/reset")
    public Result<SystemConfigView> reset(@PathVariable("id") Long id, @RequestBody(required = false) SystemConfigSaveCommand command, HttpServletRequest request) {
        String reason = command == null ? null : command.changeReason();
        return Result.ok(systemConfigService.resetToDefault(id, reason, operatorId(request), operatorName(request), clientIp(request)));
    }

    @GetMapping("/{id}/changes")
    public Result<List<SystemConfigChangeLogView>> changes(
            @PathVariable("id") Long id,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(systemConfigService.changes(id, limit));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id, @RequestBody(required = false) SystemConfigSaveCommand command, HttpServletRequest request) {
        String reason = command == null ? null : command.changeReason();
        systemConfigService.delete(id, reason, operatorId(request), operatorName(request), clientIp(request));
        return Result.ok();
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

    private String operatorName(HttpServletRequest request) {
        String value = firstHeader(request, "X-User-Name", "x-user-name", "X-Username", "x-username");
        return value == null || value.isBlank() ? "admin" : value.trim();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = firstHeader(request, "X-Forwarded-For", "x-forwarded-for");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
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
