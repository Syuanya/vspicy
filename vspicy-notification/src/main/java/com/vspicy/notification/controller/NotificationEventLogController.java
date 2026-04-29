package com.vspicy.notification.controller;

import com.vspicy.common.core.Result;
import com.vspicy.notification.dto.NotificationEventLogItem;
import com.vspicy.notification.service.NotificationEventDispatchService;
import com.vspicy.notification.service.NotificationEventLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications/event-logs")
public class NotificationEventLogController {
    private final NotificationEventLogService eventLogService;
    private final NotificationEventDispatchService dispatchService;

    public NotificationEventLogController(
            NotificationEventLogService eventLogService,
            NotificationEventDispatchService dispatchService
    ) {
        this.eventLogService = eventLogService;
        this.dispatchService = dispatchService;
    }

    @GetMapping
    public Result<List<NotificationEventLogItem>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "receiverUserId", required = false) Long receiverUserId,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(eventLogService.list(status, eventType, receiverUserId, limit));
    }

    @PostMapping("/{eventId}/retry")
    public Result<String> retry(@PathVariable("eventId") String eventId) {
        return Result.ok(dispatchService.retry(eventId));
    }
}
