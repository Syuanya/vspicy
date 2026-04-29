package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoUploadTraceLinkCommand;
import com.vspicy.video.dto.VideoUploadTraceView;
import com.vspicy.video.service.VideoUploadTraceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos/upload/traces")
public class VideoUploadTraceController {
    private final VideoUploadTraceService traceService;

    public VideoUploadTraceController(VideoUploadTraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping
    public Result<List<VideoUploadTraceView>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(traceService.list(keyword, limit));
    }

    @GetMapping("/by-video/{videoId}")
    public Result<List<VideoUploadTraceView>> byVideo(@PathVariable Long videoId) {
        return Result.ok(traceService.byVideo(videoId));
    }

    @GetMapping("/by-record/{recordId}")
    public Result<List<VideoUploadTraceView>> byRecord(@PathVariable Long recordId) {
        return Result.ok(traceService.byRecord(recordId));
    }

    @GetMapping("/by-task/{uploadTaskId}")
    public Result<List<VideoUploadTraceView>> byTask(@PathVariable String uploadTaskId) {
        return Result.ok(traceService.byTask(uploadTaskId));
    }

    @PostMapping("/link")
    public Result<VideoUploadTraceView> link(
            @RequestBody VideoUploadTraceLinkCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(traceService.link(command, currentUserId(request)));
    }

    private Long currentUserId(HttpServletRequest request) {
        String value = request.getHeader("X-User-Id");
        if (value == null || value.isBlank()) {
            return 1L;
        }
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            return 1L;
        }
    }
}
