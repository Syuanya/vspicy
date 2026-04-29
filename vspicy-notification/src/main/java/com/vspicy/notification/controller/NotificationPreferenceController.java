package com.vspicy.notification.controller;

import com.vspicy.common.core.Result;
import com.vspicy.notification.dto.NotificationPreferenceItem;
import com.vspicy.notification.dto.NotificationPreferenceSaveCommand;
import com.vspicy.notification.service.NotificationPreferenceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications/preferences")
public class NotificationPreferenceController {
    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public Result<List<NotificationPreferenceItem>> list(
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request
    ) {
        return Result.ok(preferenceService.list(resolveUserId(userId, request)));
    }

    @PutMapping
    public Result<List<NotificationPreferenceItem>> save(
            @RequestBody NotificationPreferenceSaveCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(preferenceService.save(command, currentUserId(request)));
    }

    private Long resolveUserId(Long userId, HttpServletRequest request) {
        if (userId != null) {
            return userId;
        }
        Long headerUserId = currentUserId(request);
        return headerUserId == null ? 1L : headerUserId;
    }

    private Long currentUserId(HttpServletRequest request) {
        String value = request.getHeader("X-User-Id");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
