package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.HlsIntegrityResult;
import com.vspicy.video.service.HlsIntegrityService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/upload/storage")
public class HlsIntegrityController {
    private final HlsIntegrityService hlsIntegrityService;

    public HlsIntegrityController(HlsIntegrityService hlsIntegrityService) {
        this.hlsIntegrityService = hlsIntegrityService;
    }

    @GetMapping("/hls-integrity")
    public Result<HlsIntegrityResult> scan(
            @RequestParam(value = "prefix", required = false, defaultValue = "videos/") String prefix,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit
    ) {
        return Result.ok(hlsIntegrityService.scan(prefix, limit));
    }

    @GetMapping("/hls-integrity/object")
    public Result<HlsIntegrityResult> checkObject(@RequestParam("objectKey") String objectKey) {
        return Result.ok(hlsIntegrityService.checkObject(objectKey));
    }
}
