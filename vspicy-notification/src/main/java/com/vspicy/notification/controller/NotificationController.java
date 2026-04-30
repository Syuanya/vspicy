package com.vspicy.notification.controller;

import com.vspicy.common.core.Result;
import com.vspicy.notification.dto.NotificationBatchCommand;
import com.vspicy.notification.dto.NotificationCreateCommand;
import com.vspicy.notification.dto.NotificationInboxItem;
import com.vspicy.notification.dto.UnreadCountResponse;
import com.vspicy.notification.service.NotificationService;
import com.vspicy.notification.service.NotificationSseService;
import com.vspicy.notification.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationSseService sseService;

    public NotificationController(NotificationService notificationService, NotificationSseService sseService) {
        this.notificationService = notificationService;
        this.sseService = sseService;
    }

    @GetMapping("/stream")
    public SseEmitter stream(HttpServletRequest request) {
        return sseService.connect(CurrentUser.id(request));
    }

    @PostMapping("/system")
    public Result<Long> publishSystem(
            @RequestBody NotificationCreateCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(notificationService.publishSystem(command, CurrentUser.optionalId(request)));
    }

    @GetMapping("/inbox")
    public Result<List<NotificationInboxItem>> inbox(
            @RequestParam(value = "readStatus", required = false) Integer readStatus,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit,
            HttpServletRequest request
    ) {
        return Result.ok(notificationService.inbox(CurrentUser.id(request), readStatus, limit));
    }

    @GetMapping("/unread-count")
    public Result<UnreadCountResponse> unreadCount(HttpServletRequest request) {
        return Result.ok(notificationService.unreadCount(CurrentUser.id(request)));
    }

    @PostMapping("/inbox/{inboxId}/read")
    public Result<String> markRead(
            @PathVariable("inboxId") Long inboxId,
            HttpServletRequest request
    ) {
        notificationService.markRead(CurrentUser.id(request), inboxId);
        return Result.ok("ok");
    }

    @PostMapping("/inbox/read-batch")
    public Result<Integer> markBatchRead(
            @RequestBody NotificationBatchCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(notificationService.markBatchRead(CurrentUser.id(request), command));
    }

    @PostMapping("/inbox/read-all")
    public Result<String> markAllRead(HttpServletRequest request) {
        notificationService.markAllRead(CurrentUser.id(request));
        return Result.ok("ok");
    }

    @DeleteMapping("/inbox/{inboxId}")
    public Result<String> deleteInbox(
            @PathVariable("inboxId") Long inboxId,
            HttpServletRequest request
    ) {
        notificationService.deleteInbox(CurrentUser.id(request), inboxId);
        return Result.ok("ok");
    }

    @PostMapping("/inbox/delete-batch")
    public Result<Integer> deleteBatch(
            @RequestBody NotificationBatchCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(notificationService.deleteBatch(CurrentUser.id(request), command));
    }

    @PostMapping("/inbox/clear-read")
    public Result<Integer> clearRead(HttpServletRequest request) {
        return Result.ok(notificationService.clearRead(CurrentUser.id(request)));
    }

    @GetMapping("/announcements")
    public Result<List<NotificationInboxItem>> announcements(
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        return Result.ok(notificationService.announcements(limit));
    }

    @GetMapping("/online-count")
    public Result<Integer> onlineCount(HttpServletRequest request) {
        return Result.ok(sseService.onlineCount(CurrentUser.id(request)));
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("vspicy-notification ok");
    }
}
