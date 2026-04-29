package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoStorageOpsConsoleView;
import com.vspicy.video.service.VideoStorageOpsConsoleService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/upload/storage/ops")
public class VideoStorageOpsConsoleController {
    private final VideoStorageOpsConsoleService consoleService;

    public VideoStorageOpsConsoleController(VideoStorageOpsConsoleService consoleService) {
        this.consoleService = consoleService;
    }

    @GetMapping("/console")
    public Result<VideoStorageOpsConsoleView> console(
            @RequestParam(value = "prefix", required = false, defaultValue = "videos/") String prefix,
            @RequestParam(value = "limit", required = false, defaultValue = "1000") Integer limit,
            @RequestParam(value = "threshold", required = false, defaultValue = "80") Integer threshold
    ) {
        return Result.ok(consoleService.console(prefix, limit, threshold));
    }
}
