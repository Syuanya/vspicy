package com.vspicy.admin.controller;

import com.vspicy.admin.audit.AuditLog;
import com.vspicy.admin.dto.SystemReleaseCheckCommand;
import com.vspicy.admin.dto.SystemReleaseCheckItemView;
import com.vspicy.admin.dto.SystemReleaseCommand;
import com.vspicy.admin.dto.SystemReleaseOverviewView;
import com.vspicy.admin.dto.SystemReleaseView;
import com.vspicy.admin.service.AdminSystemReleaseService;
import com.vspicy.common.core.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/releases")
public class AdminSystemReleaseController {
    private final AdminSystemReleaseService releaseService;

    public AdminSystemReleaseController(AdminSystemReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping("/overview")
    public Result<SystemReleaseOverviewView> overview(
            @RequestParam(value = "days", required = false, defaultValue = "30") Integer days
    ) {
        return Result.ok(releaseService.overview(days));
    }

    @GetMapping
    public Result<List<SystemReleaseView>> list(
            @RequestParam(value = "environment", required = false) String environment,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            @RequestParam(value = "serviceName", required = false) String serviceName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(releaseService.list(environment, status, riskLevel, serviceName, keyword, limit));
    }

    @GetMapping("/{id}")
    public Result<SystemReleaseView> get(@PathVariable("id") Long id) {
        return Result.ok(releaseService.get(id));
    }

    @AuditLog(type = "CREATE", title = "创建发布单")
    @PostMapping
    public Result<SystemReleaseView> create(@RequestBody SystemReleaseCommand command, HttpServletRequest request) {
        return Result.ok(releaseService.create(command, currentUserId(request)));
    }

    @AuditLog(type = "UPDATE", title = "更新发布单")
    @PutMapping("/{id}")
    public Result<SystemReleaseView> update(@PathVariable("id") Long id,
                                            @RequestBody SystemReleaseCommand command,
                                            HttpServletRequest request) {
        return Result.ok(releaseService.update(id, command, currentUserId(request)));
    }

    @AuditLog(type = "UPDATE", title = "开始发布")
    @PostMapping("/{id}/start")
    public Result<SystemReleaseView> start(@PathVariable("id") Long id,
                                           @RequestBody(required = false) SystemReleaseCommand command,
                                           HttpServletRequest request) {
        return Result.ok(releaseService.start(id, command, currentUserId(request)));
    }

    @AuditLog(type = "UPDATE", title = "标记发布成功")
    @PostMapping("/{id}/success")
    public Result<SystemReleaseView> success(@PathVariable("id") Long id,
                                             @RequestBody(required = false) SystemReleaseCommand command,
                                             HttpServletRequest request) {
        return Result.ok(releaseService.success(id, command, currentUserId(request)));
    }

    @AuditLog(type = "UPDATE", title = "标记发布失败")
    @PostMapping("/{id}/fail")
    public Result<SystemReleaseView> fail(@PathVariable("id") Long id,
                                          @RequestBody(required = false) SystemReleaseCommand command,
                                          HttpServletRequest request) {
        return Result.ok(releaseService.fail(id, command, currentUserId(request)));
    }

    @AuditLog(type = "UPDATE", title = "发布回滚")
    @PostMapping("/{id}/rollback")
    public Result<SystemReleaseView> rollback(@PathVariable("id") Long id,
                                              @RequestBody(required = false) SystemReleaseCommand command,
                                              HttpServletRequest request) {
        return Result.ok(releaseService.rollback(id, command, currentUserId(request)));
    }

    @AuditLog(type = "DELETE", title = "删除发布单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        releaseService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/checks")
    public Result<List<SystemReleaseCheckItemView>> checks(@PathVariable("id") Long id) {
        return Result.ok(releaseService.checks(id));
    }

    @AuditLog(type = "CREATE", title = "新增发布检查项")
    @PostMapping("/{id}/checks")
    public Result<SystemReleaseCheckItemView> createCheck(@PathVariable("id") Long id,
                                                          @RequestBody SystemReleaseCheckCommand command) {
        return Result.ok(releaseService.createCheck(id, command));
    }

    @AuditLog(type = "UPDATE", title = "更新发布检查项")
    @PutMapping("/checks/{checkId}")
    public Result<SystemReleaseCheckItemView> updateCheck(@PathVariable("checkId") Long checkId,
                                                          @RequestBody SystemReleaseCheckCommand command) {
        return Result.ok(releaseService.updateCheck(checkId, command));
    }

    @AuditLog(type = "UPDATE", title = "通过发布检查项")
    @PostMapping("/checks/{checkId}/pass")
    public Result<SystemReleaseCheckItemView> passCheck(@PathVariable("checkId") Long checkId,
                                                        @RequestBody(required = false) SystemReleaseCheckCommand command,
                                                        HttpServletRequest request) {
        return Result.ok(releaseService.passCheck(checkId, command, currentUserId(request)));
    }

    @AuditLog(type = "UPDATE", title = "发布检查项失败")
    @PostMapping("/checks/{checkId}/fail")
    public Result<SystemReleaseCheckItemView> failCheck(@PathVariable("checkId") Long checkId,
                                                        @RequestBody(required = false) SystemReleaseCheckCommand command,
                                                        HttpServletRequest request) {
        return Result.ok(releaseService.failCheck(checkId, command, currentUserId(request)));
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
