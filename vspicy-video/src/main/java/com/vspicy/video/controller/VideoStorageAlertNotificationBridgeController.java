package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoStorageAlertNotificationSyncCommand;
import com.vspicy.video.dto.VideoStorageAlertNotificationSyncResult;
import com.vspicy.video.dto.VideoStorageAlertNotificationView;
import com.vspicy.video.service.VideoStorageAlertNotificationBridgeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos/upload/storage/alert-notifications")
public class VideoStorageAlertNotificationBridgeController {
    private final VideoStorageAlertNotificationBridgeService bridgeService;

    public VideoStorageAlertNotificationBridgeController(VideoStorageAlertNotificationBridgeService bridgeService) {
        this.bridgeService = bridgeService;
    }

    @GetMapping
    public Result<List<VideoStorageAlertNotificationView>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(bridgeService.list(status, limit));
    }

    @PostMapping("/sync")
    public Result<VideoStorageAlertNotificationSyncResult> sync(
            @RequestBody(required = false) VideoStorageAlertNotificationSyncCommand command
    ) {
        return Result.ok(bridgeService.sync(command));
    }

    @PostMapping("/{id}/retry")
    public Result<VideoStorageAlertNotificationView> retry(@PathVariable Long id) {
        return Result.ok(bridgeService.retry(id));
    }
}
