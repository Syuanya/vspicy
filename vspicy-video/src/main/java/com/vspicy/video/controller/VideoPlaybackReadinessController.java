package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoPlaybackReadinessSyncCommand;
import com.vspicy.video.dto.VideoPlaybackReadinessSyncResult;
import com.vspicy.video.dto.VideoPlaybackReadinessView;
import com.vspicy.video.service.VideoPlaybackReadinessService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/playback/readiness")
public class VideoPlaybackReadinessController {
    private final VideoPlaybackReadinessService readinessService;

    public VideoPlaybackReadinessController(VideoPlaybackReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @GetMapping("/{videoId}")
    public Result<VideoPlaybackReadinessView> readiness(@PathVariable Long videoId) {
        return Result.ok(readinessService.readiness(videoId));
    }

    @PostMapping("/{videoId}/sync")
    public Result<VideoPlaybackReadinessSyncResult> sync(
            @PathVariable Long videoId,
            @RequestBody(required = false) VideoPlaybackReadinessSyncCommand command
    ) {
        return Result.ok(readinessService.sync(videoId, command));
    }
}
