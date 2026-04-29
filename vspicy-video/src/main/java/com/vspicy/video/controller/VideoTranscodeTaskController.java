package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.TranscodeCompensationResponse;
import com.vspicy.video.dto.TranscodeRetryResponse;
import com.vspicy.video.entity.VideoTranscodeTask;
import com.vspicy.video.service.VideoTranscodeCompensationService;
import com.vspicy.video.service.VideoTranscodeTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos")
public class VideoTranscodeTaskController {
    private final VideoTranscodeTaskService transcodeTaskService;
    private final VideoTranscodeCompensationService compensationService;

    public VideoTranscodeTaskController(
            VideoTranscodeTaskService transcodeTaskService,
            VideoTranscodeCompensationService compensationService
    ) {
        this.transcodeTaskService = transcodeTaskService;
        this.compensationService = compensationService;
    }

    @GetMapping("/transcode-tasks")
    public Result<List<VideoTranscodeTask>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "videoId", required = false) Long videoId,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(transcodeTaskService.list(status, videoId, limit));
    }

    @GetMapping("/transcode-tasks/{taskId}")
    public Result<VideoTranscodeTask> getById(@PathVariable("taskId") Long taskId) {
        return Result.ok(transcodeTaskService.getById(taskId));
    }

    @PostMapping("/transcode-tasks/{taskId}/retry")
    public Result<TranscodeRetryResponse> retryTask(@PathVariable("taskId") Long taskId) {
        return Result.ok(transcodeTaskService.retryTask(taskId));
    }

    @PostMapping("/transcode-tasks/compensate")
    public Result<TranscodeCompensationResponse> compensate() {
        return Result.ok(compensationService.compensate());
    }

    @PostMapping("/{videoId}/retry-transcode")
    public Result<TranscodeRetryResponse> retryByVideoId(@PathVariable("videoId") Long videoId) {
        return Result.ok(transcodeTaskService.retryByVideoId(videoId));
    }
}
