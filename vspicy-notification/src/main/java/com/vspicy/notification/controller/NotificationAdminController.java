package com.vspicy.notification.controller;

import com.vspicy.common.core.Result;
import com.vspicy.notification.dto.NotificationAdminInboxItem;
import com.vspicy.notification.dto.NotificationAdminInboxSummaryView;
import com.vspicy.notification.dto.NotificationOverviewView;
import com.vspicy.notification.service.NotificationOverviewService;
import com.vspicy.notification.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications/admin")
public class NotificationAdminController {
    private final NotificationOverviewService overviewService;
    private final NotificationService notificationService;

    public NotificationAdminController(
            NotificationOverviewService overviewService,
            NotificationService notificationService
    ) {
        this.overviewService = overviewService;
        this.notificationService = notificationService;
    }

    @GetMapping("/overview")
    public Result<NotificationOverviewView> overview(
            @RequestParam(value = "days", required = false, defaultValue = "7") Integer days
    ) {
        return Result.ok(overviewService.overview(days));
    }

    @GetMapping("/inbox")
    public Result<List<NotificationAdminInboxItem>> inbox(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "readStatus", required = false) Integer readStatus,
            @RequestParam(value = "deleted", required = false) Integer deleted,
            @RequestParam(value = "notificationType", required = false) String notificationType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(notificationService.adminInbox(userId, readStatus, deleted, notificationType, keyword, limit));
    }

    @GetMapping("/inbox/{inboxId}")
    public Result<NotificationAdminInboxItem> inboxDetail(@PathVariable("inboxId") Long inboxId) {
        return Result.ok(notificationService.adminInboxDetail(inboxId));
    }

    @GetMapping("/users/{userId}/inbox-summary")
    public Result<NotificationAdminInboxSummaryView> userInboxSummary(@PathVariable("userId") Long userId) {
        return Result.ok(notificationService.adminInboxSummary(userId));
    }
}
