package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.*;
import com.vspicy.video.service.ObjectCleanupApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos/upload/storage/cleanup-requests")
public class ObjectCleanupApprovalController {
    private final ObjectCleanupApprovalService cleanupApprovalService;

    public ObjectCleanupApprovalController(ObjectCleanupApprovalService cleanupApprovalService) {
        this.cleanupApprovalService = cleanupApprovalService;
    }

    @GetMapping
    public Result<List<ObjectCleanupRequestView>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(cleanupApprovalService.list(status, limit));
    }

    @PostMapping("/generate")
    public Result<ObjectCleanupGenerateResult> generate(@RequestBody(required = false) ObjectCleanupGenerateCommand command) {
        return Result.ok(cleanupApprovalService.generate(command));
    }

    @PostMapping("/{id}/approve")
    public Result<ObjectCleanupRequestView> approve(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(cleanupApprovalService.approve(id, currentUserId(request)));
    }

    @PostMapping("/{id}/reject")
    public Result<ObjectCleanupRequestView> reject(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(cleanupApprovalService.reject(id, currentUserId(request)));
    }

    @PostMapping("/{id}/execute")
    public Result<ObjectCleanupExecuteResult> executeOne(
            @PathVariable Long id,
            @RequestBody(required = false) ObjectCleanupExecuteCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(cleanupApprovalService.executeOne(id, command, currentUserId(request)));
    }

    @PostMapping("/execute-approved")
    public Result<ObjectCleanupExecuteResult> executeApproved(
            @RequestBody(required = false) ObjectCleanupExecuteCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(cleanupApprovalService.executeApproved(command, currentUserId(request)));
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
