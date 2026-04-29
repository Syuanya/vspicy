package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoUploadQuotaCheckResponse;
import com.vspicy.video.dto.VideoUploadQuotaConfirmCommand;
import com.vspicy.video.dto.VideoUploadQuotaReleaseCommand;
import com.vspicy.video.dto.VideoUploadQuotaUsageView;
import com.vspicy.video.dto.VideoUploadRecordView;
import com.vspicy.video.service.VideoUploadQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos/upload/quota")
public class VideoUploadQuotaController {
    private final VideoUploadQuotaService quotaService;

    public VideoUploadQuotaController(VideoUploadQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping
    public Result<VideoUploadQuotaUsageView> usage(HttpServletRequest request) {
        return Result.ok(quotaService.usage(currentUserId(request)));
    }

    @GetMapping("/records")
    public Result<List<VideoUploadRecordView>> records(
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit,
            HttpServletRequest request
    ) {
        return Result.ok(quotaService.records(currentUserId(request), limit));
    }

    @GetMapping("/check")
    public Result<VideoUploadQuotaCheckResponse> check(
            @RequestParam(value = "sizeMb", required = false, defaultValue = "0") Long sizeMb,
            HttpServletRequest request
    ) {
        return Result.ok(quotaService.check(currentUserId(request), sizeMb));
    }

    @PostMapping("/confirm")
    public Result<VideoUploadQuotaCheckResponse> confirm(
            @RequestBody VideoUploadQuotaConfirmCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(quotaService.confirm(command, currentUserId(request)));
    }

    @PostMapping("/release")
    public Result<VideoUploadQuotaUsageView> release(
            @RequestBody VideoUploadQuotaReleaseCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(quotaService.release(command, currentUserId(request)));
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
