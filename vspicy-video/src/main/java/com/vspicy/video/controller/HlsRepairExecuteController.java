package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.HlsRepairExecuteCommand;
import com.vspicy.video.dto.HlsRepairExecuteResult;
import com.vspicy.video.service.HlsRepairExecuteService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/upload/storage/hls-repair/execute")
public class HlsRepairExecuteController {
    private final HlsRepairExecuteService executeService;

    public HlsRepairExecuteController(HlsRepairExecuteService executeService) {
        this.executeService = executeService;
    }

    @GetMapping("/preview")
    public Result<HlsRepairExecuteResult> preview(
            @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit
    ) {
        return Result.ok(executeService.preview(limit));
    }

    @PostMapping
    public Result<HlsRepairExecuteResult> execute(@RequestBody(required = false) HlsRepairExecuteCommand command) {
        return Result.ok(executeService.execute(command));
    }

    @PostMapping("/{id}")
    public Result<HlsRepairExecuteResult> executeOne(
            @PathVariable Long id,
            @RequestBody(required = false) HlsRepairExecuteCommand command
    ) {
        return Result.ok(executeService.executeOne(id, command));
    }
}
