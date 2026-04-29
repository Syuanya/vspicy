package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoStorageAlertGenerateCommand;
import com.vspicy.video.dto.VideoStorageAlertGenerateResult;
import com.vspicy.video.dto.VideoStorageAlertView;
import com.vspicy.video.service.VideoStorageAlertService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos/upload/storage/alerts")
public class VideoStorageAlertController {
    private final VideoStorageAlertService alertService;

    public VideoStorageAlertController(VideoStorageAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public Result<List<VideoStorageAlertView>> list(
            @RequestParam(value = "status", required = false, defaultValue = "OPEN") String status,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(alertService.list(status, level, limit));
    }

    @PostMapping("/generate")
    public Result<VideoStorageAlertGenerateResult> generate(
            @RequestBody(required = false) VideoStorageAlertGenerateCommand command
    ) {
        return Result.ok(alertService.generate(command));
    }

    @PostMapping("/{id}/ack")
    public Result<VideoStorageAlertView> ack(@PathVariable Long id) {
        return Result.ok(alertService.ack(id));
    }

    @PostMapping("/{id}/resolve")
    public Result<VideoStorageAlertView> resolve(@PathVariable Long id) {
        return Result.ok(alertService.resolve(id));
    }
}
