package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoTranscodeDispatchView;
import com.vspicy.video.mq.VideoTranscodeDispatcher;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/transcode/dispatch")
public class VideoTranscodeDispatchController {
    private final VideoTranscodeDispatcher dispatcher;

    public VideoTranscodeDispatchController(VideoTranscodeDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @GetMapping("/health")
    public Result<VideoTranscodeDispatchView> health() {
        return Result.ok(dispatcher.health());
    }

    @PostMapping("/{taskId}")
    public Result<VideoTranscodeDispatchView> dispatch(@PathVariable Long taskId) {
        return Result.ok(dispatcher.dispatch(taskId));
    }

    @PostMapping("/{taskId}/local")
    public Result<VideoTranscodeDispatchView> dispatchLocal(@PathVariable Long taskId) {
        return Result.ok(dispatcher.dispatchLocal(taskId));
    }
}
