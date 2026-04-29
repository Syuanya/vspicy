package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoStorageCleanupCommand;
import com.vspicy.video.dto.VideoStorageCleanupResult;
import com.vspicy.video.dto.VideoStorageScanResult;
import com.vspicy.video.service.VideoStorageScanService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/upload/storage")
public class VideoStorageScanController {
    private final VideoStorageScanService storageScanService;

    public VideoStorageScanController(VideoStorageScanService storageScanService) {
        this.storageScanService = storageScanService;
    }

    @GetMapping("/scan")
    public Result<VideoStorageScanResult> scan(
            @RequestParam(value = "prefix", required = false, defaultValue = "videos/") String prefix,
            @RequestParam(value = "limit", required = false, defaultValue = "1000") Integer limit
    ) {
        return Result.ok(storageScanService.scan(prefix, limit));
    }

    @PostMapping("/cleanup")
    public Result<VideoStorageCleanupResult> cleanup(@RequestBody(required = false) VideoStorageCleanupCommand command) {
        return Result.ok(storageScanService.cleanup(command));
    }
}
