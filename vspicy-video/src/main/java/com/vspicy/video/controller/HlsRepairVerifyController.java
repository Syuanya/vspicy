package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.HlsRepairVerifyCommand;
import com.vspicy.video.dto.HlsRepairVerifyResult;
import com.vspicy.video.service.HlsRepairVerifyService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/upload/storage/hls-repair/verify")
public class HlsRepairVerifyController {
    private final HlsRepairVerifyService verifyService;

    public HlsRepairVerifyController(HlsRepairVerifyService verifyService) {
        this.verifyService = verifyService;
    }

    @GetMapping("/preview")
    public Result<HlsRepairVerifyResult> preview(
            @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit
    ) {
        return Result.ok(verifyService.preview(limit));
    }

    @PostMapping
    public Result<HlsRepairVerifyResult> verify(@RequestBody(required = false) HlsRepairVerifyCommand command) {
        return Result.ok(verifyService.verify(command));
    }

    @PostMapping("/{id}")
    public Result<HlsRepairVerifyResult> verifyOne(
            @PathVariable Long id,
            @RequestBody(required = false) HlsRepairVerifyCommand command
    ) {
        return Result.ok(verifyService.verifyOne(id, command));
    }
}
