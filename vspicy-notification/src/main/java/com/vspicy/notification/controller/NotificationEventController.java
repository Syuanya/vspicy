package com.vspicy.notification.controller;

import com.vspicy.common.core.Result;
import com.vspicy.notification.dto.BusinessNotificationEventCommand;
import com.vspicy.notification.service.NotificationEventService;
import com.vspicy.notification.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications/events")
public class NotificationEventController {
    private final NotificationEventService eventService;

    public NotificationEventController(NotificationEventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("/transcode")
    public Result<Long> transcode(
            @RequestBody BusinessNotificationEventCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(eventService.transcode(command, CurrentUser.requireUserId(request)));
    }

    @PostMapping("/audit")
    public Result<Long> audit(
            @RequestBody BusinessNotificationEventCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(eventService.audit(command, CurrentUser.requireUserId(request)));
    }

    @PostMapping("/interaction")
    public Result<Long> interaction(
            @RequestBody BusinessNotificationEventCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(eventService.interaction(command, CurrentUser.requireUserId(request)));
    }

    @PostMapping("/security")
    public Result<Long> security(
            @RequestBody BusinessNotificationEventCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(eventService.security(command, CurrentUser.requireUserId(request)));
    }

    @PostMapping("/custom")
    public Result<Long> custom(
            @RequestBody BusinessNotificationEventCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(eventService.custom(command, CurrentUser.requireUserId(request)));
    }
}
