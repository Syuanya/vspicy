package com.vspicy.admin.controller;

import com.vspicy.admin.audit.AuditLog;
import com.vspicy.admin.dto.SystemConfigCommand;
import com.vspicy.admin.dto.SystemConfigGroupView;
import com.vspicy.admin.dto.SystemConfigView;
import com.vspicy.admin.service.SystemConfigService;
import com.vspicy.common.core.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system-configs")
public class SystemConfigController {
    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    public Result<List<SystemConfigView>> list(
            @RequestParam(value = "groupCode", required = false) String groupCode,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(systemConfigService.list(groupCode, keyword, status, limit));
    }

    @GetMapping("/groups")
    public Result<List<SystemConfigGroupView>> groups() {
        return Result.ok(systemConfigService.groups());
    }

    @GetMapping("/{id}")
    public Result<SystemConfigView> get(@PathVariable("id") Long id) {
        return Result.ok(systemConfigService.get(id));
    }

    @AuditLog(type = "CREATE", title = "创建系统配置")
    @PostMapping
    public Result<SystemConfigView> create(@RequestBody SystemConfigCommand command) {
        return Result.ok(systemConfigService.create(command));
    }

    @AuditLog(type = "UPDATE", title = "更新系统配置")
    @PutMapping("/{id}")
    public Result<SystemConfigView> update(@PathVariable("id") Long id, @RequestBody SystemConfigCommand command) {
        return Result.ok(systemConfigService.update(id, command));
    }

    @AuditLog(type = "UPDATE", title = "启用系统配置")
    @PostMapping("/{id}/enable")
    public Result<SystemConfigView> enable(@PathVariable("id") Long id) {
        return Result.ok(systemConfigService.enable(id));
    }

    @AuditLog(type = "UPDATE", title = "停用系统配置")
    @PostMapping("/{id}/disable")
    public Result<SystemConfigView> disable(@PathVariable("id") Long id) {
        return Result.ok(systemConfigService.disable(id));
    }

    @AuditLog(type = "DELETE", title = "删除系统配置")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        systemConfigService.delete(id);
        return Result.ok();
    }
}
