package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.service.VideoUploadGuardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/upload")
public class VideoUploadCheckController {
    private final VideoUploadGuardService uploadGuardService;

    public VideoUploadCheckController(VideoUploadGuardService uploadGuardService) {
        this.uploadGuardService = uploadGuardService;
    }

    @GetMapping("/check")
    public Result<VideoUploadGuardService.UploadCheckView> check(
            @RequestParam(value = "sizeMb", required = false, defaultValue = "0") Long sizeMb,
            HttpServletRequest request
    ) {
        return Result.ok(uploadGuardService.checkOnly(currentUserId(request), sizeMb));
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
