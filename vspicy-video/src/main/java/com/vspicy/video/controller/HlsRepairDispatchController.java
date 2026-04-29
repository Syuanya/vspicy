package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.HlsRepairDispatchCommand;
import com.vspicy.video.dto.HlsRepairDispatchResult;
import com.vspicy.video.service.HlsRepairDispatchService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/upload/storage/hls-repair/dispatch")
public class HlsRepairDispatchController {
    private final HlsRepairDispatchService dispatchService;

    public HlsRepairDispatchController(HlsRepairDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @GetMapping("/preview")
    public Result<HlsRepairDispatchResult> preview(
            @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit
    ) {
        return Result.ok(dispatchService.preview(limit));
    }

    @PostMapping
    public Result<HlsRepairDispatchResult> dispatch(@RequestBody(required = false) HlsRepairDispatchCommand command) {
        return Result.ok(dispatchService.dispatch(command));
    }
}
