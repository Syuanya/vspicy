package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoTranscodeProgressView;
import com.vspicy.video.service.VideoTranscodeProgressService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/transcode/progress")
public class VideoTranscodeProgressController {
    private final VideoTranscodeProgressService progressService;

    public VideoTranscodeProgressController(VideoTranscodeProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping("/video/{videoId}")
    public Result<VideoTranscodeProgressView> byVideoId(@PathVariable Long videoId) {
        return Result.ok(progressService.byVideoId(videoId));
    }

    @GetMapping("/task/{taskId}")
    public Result<VideoTranscodeProgressView> byTaskId(@PathVariable Long taskId) {
        return Result.ok(progressService.byTaskId(taskId));
    }
}
