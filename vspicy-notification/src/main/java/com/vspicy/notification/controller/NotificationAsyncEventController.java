package com.vspicy.notification.controller;

import com.vspicy.common.core.Result;
import com.vspicy.notification.dto.BusinessNotificationEventCommand;
import com.vspicy.notification.service.NotificationEventDispatchService;
import com.vspicy.notification.web.CurrentUser;
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
        return Result.ok(dispatchService.dispatch("TRANSCODE", command, CurrentUser.requireUserId(request)));
    }

    @PostMapping("/audit")
    public Result<String> audit(
            @RequestBody BusinessNotificationEventCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(dispatchService.dispatch("AUDIT", command, CurrentUser.requireUserId(request)));
    }

    @PostMapping("/interaction")
    public Result<String> interaction(
            @RequestBody BusinessNotificationEventCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(dispatchService.dispatch("INTERACTION", command, CurrentUser.requireUserId(request)));
    }

    @PostMapping("/security")
    public Result<String> security(
            @RequestBody BusinessNotificationEventCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(dispatchService.dispatch("SECURITY", command, CurrentUser.requireUserId(request)));
    }

    @PostMapping("/custom")
    public Result<String> custom(
            @RequestBody BusinessNotificationEventCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(dispatchService.dispatch("CUSTOM", command, CurrentUser.requireUserId(request)));
    }
}
