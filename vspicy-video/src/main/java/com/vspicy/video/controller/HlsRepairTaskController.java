package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.*;
import com.vspicy.video.service.HlsRepairTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos/upload/storage/hls-repair")
public class HlsRepairTaskController {
    private final HlsRepairTaskService repairTaskService;

    public HlsRepairTaskController(HlsRepairTaskService repairTaskService) {
        this.repairTaskService = repairTaskService;
    }

    @GetMapping("/tasks")
    public Result<List<HlsRepairTaskView>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(repairTaskService.list(status, limit));
    }

    @PostMapping("/generate")
    public Result<HlsRepairGenerateResult> generate(@RequestBody(required = false) HlsRepairGenerateCommand command) {
        return Result.ok(repairTaskService.generate(command));
    }

    @PostMapping("/generate-from-alerts")
    public Result<HlsRepairGenerateResult> generateFromAlerts(
            @RequestBody(required = false) HlsRepairGenerateFromAlertCommand command
    ) {
        return Result.ok(repairTaskService.generateFromAlerts(command));
    }

    @PostMapping("/tasks/{id}/retry")
    public Result<HlsRepairTaskView> retry(@PathVariable Long id) {
        return Result.ok(repairTaskService.retry(id));
    }

    @PostMapping("/tasks/{id}/cancel")
    public Result<HlsRepairTaskView> cancel(@PathVariable Long id) {
        return Result.ok(repairTaskService.cancel(id));
    }

    @PostMapping("/tasks/{id}/success")
    public Result<HlsRepairTaskView> success(@PathVariable Long id) {
        return Result.ok(repairTaskService.markSuccess(id));
    }

    @PostMapping("/tasks/{id}/fail")
    public Result<HlsRepairTaskView> fail(
            @PathVariable Long id,
            @RequestBody(required = false) HlsRepairFailCommand command
    ) {
        return Result.ok(repairTaskService.markFailed(id, command));
    }
}
