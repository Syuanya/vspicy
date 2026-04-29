package com.vspicy.notification.controller;

import com.vspicy.common.core.Result;
import com.vspicy.notification.dto.BusinessNotificationEventCommand;
import com.vspicy.notification.service.NotificationEventDispatchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications/events/async")
public class NotificationAsyncEventController {
    private final NotificationEventDispatchService dispatchService;

    public NotificationAsyncEventController(NotificationEventDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostMapping("/transcode")
    public Result<String> transcode(
            @RequestBody BusinessNotificationEventCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(dispatchService.dispatch("TRANSCODE", command, currentUserId(request)));
    }

    @PostMapping("/audit")
    public Result<String> audit(
            @RequestBody BusinessNotificationEventCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(dispatchService.dispatch("AUDIT", command, currentUserId(request)));
    }

    @PostMapping("/interaction")
    public Result<String> interaction(
            @RequestBody BusinessNotificationEventCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(dispatchService.dispatch("INTERACTION", command, currentUserId(request)));
    }

    @PostMapping("/security")
    public Result<String> security(
            @RequestBody BusinessNotificationEventCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(dispatchService.dispatch("SECURITY", command, currentUserId(request)));
    }

    @PostMapping("/custom")
    public Result<String> custom(
            @RequestBody BusinessNotificationEventCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(dispatchService.dispatch("CUSTOM", command, currentUserId(request)));
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
