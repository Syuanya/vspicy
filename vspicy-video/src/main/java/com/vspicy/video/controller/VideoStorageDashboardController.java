package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoStorageDashboardView;
import com.vspicy.video.service.VideoStorageDashboardService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/upload/storage")
public class VideoStorageDashboardController {
    private final VideoStorageDashboardService dashboardService;

    public VideoStorageDashboardController(VideoStorageDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public Result<VideoStorageDashboardView> dashboard(
            @RequestParam(value = "prefix", required = false, defaultValue = "videos/") String prefix,
            @RequestParam(value = "limit", required = false, defaultValue = "1000") Integer limit,
            @RequestParam(value = "threshold", required = false, defaultValue = "80") Integer threshold
    ) {
        return Result.ok(dashboardService.dashboard(prefix, limit, threshold));
    }
}
