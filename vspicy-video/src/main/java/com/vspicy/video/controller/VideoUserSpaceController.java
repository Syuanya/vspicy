package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.UserSpaceSummaryView;
import com.vspicy.video.dto.VideoUploadQuotaReconcileResult;
import com.vspicy.video.dto.VideoUploadRecordView;
import com.vspicy.video.service.VideoUserSpaceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos/upload/space")
public class VideoUserSpaceController {
    private final VideoUserSpaceService userSpaceService;

    public VideoUserSpaceController(VideoUserSpaceService userSpaceService) {
        this.userSpaceService = userSpaceService;
    }

    @GetMapping("/users")
    public Result<List<UserSpaceSummaryView>> users(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(userSpaceService.users(keyword, limit));
    }

    @GetMapping("/users/{userId}/records")
    public Result<List<VideoUploadRecordView>> records(
            @PathVariable Long userId,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(userSpaceService.records(userId, limit));
    }

    @PostMapping("/users/{userId}/reconcile")
    public Result<VideoUploadQuotaReconcileResult> reconcile(@PathVariable Long userId) {
        return Result.ok(userSpaceService.reconcile(userId));
    }
}
