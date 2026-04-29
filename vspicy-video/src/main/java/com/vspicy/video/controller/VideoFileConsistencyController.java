package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoFileConsistencyResult;
import com.vspicy.video.service.VideoFileConsistencyService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/upload/storage")
public class VideoFileConsistencyController {
    private final VideoFileConsistencyService consistencyService;

    public VideoFileConsistencyController(VideoFileConsistencyService consistencyService) {
        this.consistencyService = consistencyService;
    }

    @GetMapping("/file-consistency")
    public Result<VideoFileConsistencyResult> check(
            @RequestParam(value = "prefix", required = false, defaultValue = "videos/") String prefix,
            @RequestParam(value = "limit", required = false, defaultValue = "1000") Integer limit
    ) {
        return Result.ok(consistencyService.check(prefix, limit));
    }
}
