package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoUploadQuotaReconcilePreview;
import com.vspicy.video.dto.VideoUploadQuotaReconcileResult;
import com.vspicy.video.service.VideoUploadQuotaReconcileService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/upload/quota/reconcile")
public class VideoUploadQuotaReconcileController {
    private final VideoUploadQuotaReconcileService reconcileService;

    public VideoUploadQuotaReconcileController(VideoUploadQuotaReconcileService reconcileService) {
        this.reconcileService = reconcileService;
    }

    @GetMapping("/preview")
    public Result<VideoUploadQuotaReconcilePreview> preview(
            @RequestParam(value = "userId", required = false) Long userId
    ) {
        return Result.ok(reconcileService.preview(userId));
    }

    @PostMapping
    public Result<VideoUploadQuotaReconcileResult> reconcile(
            @RequestParam(value = "userId", required = false) Long userId
    ) {
        return Result.ok(reconcileService.reconcile(userId));
    }
}
