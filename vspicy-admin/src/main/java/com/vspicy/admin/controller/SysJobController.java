package com.vspicy.admin.controller;

import com.vspicy.admin.audit.AuditLog;
import com.vspicy.admin.dto.SysJobCommand;
import com.vspicy.admin.dto.SysJobLogView;
import com.vspicy.admin.dto.SysJobOverviewView;
import com.vspicy.admin.dto.SysJobRunResultView;
import com.vspicy.admin.dto.SysJobView;
import com.vspicy.admin.service.SysJobService;
import com.vspicy.common.core.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/jobs")
public class SysJobController {
    private final SysJobService sysJobService;

    public SysJobController(SysJobService sysJobService) {
        this.sysJobService = sysJobService;
    }

    @GetMapping("/overview")
    public Result<SysJobOverviewView> overview() {
        return Result.ok(sysJobService.overview());
    }

    @GetMapping
    public Result<List<SysJobView>> list(
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(sysJobService.list(group, status, keyword, limit));
    }

    @GetMapping("/{id}")
    public Result<SysJobView> get(@PathVariable Long id) {
        return Result.ok(sysJobService.get(id));
    }

    @PostMapping
    @AuditLog(type = "CREATE", title = "新增系统定时任务")
    public Result<SysJobView> create(@RequestBody SysJobCommand command) {
        return Result.ok(sysJobService.create(command));
    }

    @PutMapping("/{id}")
    @AuditLog(type = "UPDATE", title = "编辑系统定时任务")
    public Result<SysJobView> update(@PathVariable Long id, @RequestBody SysJobCommand command) {
        return Result.ok(sysJobService.update(id, command));
    }

    @PostMapping("/{id}/enable")
    @AuditLog(type = "UPDATE", title = "启用系统定时任务")
    public Result<SysJobView> enable(@PathVariable Long id) {
        return Result.ok(sysJobService.enable(id));
    }

    @PostMapping("/{id}/disable")
    @AuditLog(type = "UPDATE", title = "停用系统定时任务")
    public Result<SysJobView> disable(@PathVariable Long id) {
        return Result.ok(sysJobService.disable(id));
    }

    @DeleteMapping("/{id}")
    @AuditLog(type = "DELETE", title = "删除系统定时任务")
    public Result<Void> delete(@PathVariable Long id) {
        sysJobService.delete(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/run")
    @AuditLog(type = "EXECUTE", title = "人工触发系统定时任务")
    public Result<SysJobRunResultView> run(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(sysJobService.run(id, "MANUAL", request));
    }

    @GetMapping("/logs")
    public Result<List<SysJobLogView>> logs(
            @RequestParam(value = "jobId", required = false) Long jobId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(sysJobService.logs(jobId, status, limit));
    }
}
