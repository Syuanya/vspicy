package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoPlaybackReadinessBatchCommand;
import com.vspicy.video.dto.VideoPlaybackReadinessBatchResult;
import com.vspicy.video.service.VideoPlaybackReadinessBatchService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/playback/readiness-batch")
public class VideoPlaybackReadinessBatchController {
    private final VideoPlaybackReadinessBatchService batchService;

    public VideoPlaybackReadinessBatchController(VideoPlaybackReadinessBatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping("/scan")
    public Result<VideoPlaybackReadinessBatchResult> scan(
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit,
            @RequestParam(value = "onlyProblem", required = false, defaultValue = "true") Boolean onlyProblem
    ) {
        return Result.ok(batchService.scan(limit, onlyProblem));
    }

    @PostMapping("/sync")
    public Result<VideoPlaybackReadinessBatchResult> sync(
            @RequestBody(required = false) VideoPlaybackReadinessBatchCommand command
    ) {
        return Result.ok(batchService.sync(command));
    }
}
