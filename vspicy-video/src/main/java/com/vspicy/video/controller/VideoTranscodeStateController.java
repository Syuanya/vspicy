package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoTranscodeDispatchView;
import com.vspicy.video.dto.VideoTranscodeStateCommand;
import com.vspicy.video.dto.VideoTranscodeStateStatsView;
import com.vspicy.video.dto.VideoTranscodeTaskStateView;
import com.vspicy.video.service.VideoTranscodeStateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos/transcode/state")
public class VideoTranscodeStateController {
    private final VideoTranscodeStateService stateService;

    public VideoTranscodeStateController(VideoTranscodeStateService stateService) {
        this.stateService = stateService;
    }

    @GetMapping("/stats")
    public Result<VideoTranscodeStateStatsView> stats() {
        return Result.ok(stateService.stats());
    }

    @GetMapping("/tasks")
    public Result<List<VideoTranscodeTaskStateView>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(stateService.list(status, limit));
    }

    @PostMapping("/{id}/retry")
    public Result<VideoTranscodeDispatchView> retry(
            @PathVariable Long id,
            @RequestBody(required = false) VideoTranscodeStateCommand command
    ) {
        return Result.ok(stateService.retry(id, command));
    }

    @PostMapping("/{id}/rerun")
    public Result<VideoTranscodeDispatchView> rerun(
            @PathVariable Long id,
            @RequestBody(required = false) VideoTranscodeStateCommand command
    ) {
        return Result.ok(stateService.rerun(id, command));
    }

    @PostMapping("/{id}/cancel")
    public Result<VideoTranscodeTaskStateView> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) VideoTranscodeStateCommand command
    ) {
        return Result.ok(stateService.cancel(id, command));
    }

    @PostMapping("/{id}/reset")
    public Result<VideoTranscodeTaskStateView> reset(
            @PathVariable Long id,
            @RequestBody(required = false) VideoTranscodeStateCommand command
    ) {
        return Result.ok(stateService.reset(id, command));
    }

    @PostMapping("/{id}/success")
    public Result<VideoTranscodeTaskStateView> success(
            @PathVariable Long id,
            @RequestBody(required = false) VideoTranscodeStateCommand command
    ) {
        return Result.ok(stateService.success(id, command));
    }

    @PostMapping("/{id}/fail")
    public Result<VideoTranscodeTaskStateView> fail(
            @PathVariable Long id,
            @RequestBody(required = false) VideoTranscodeStateCommand command
    ) {
        return Result.ok(stateService.fail(id, command));
    }
}
