package com.vspicy.notification.controller;

import com.vspicy.common.core.Result;
import com.vspicy.notification.dto.NotificationCreateCommand;
import com.vspicy.notification.dto.NotificationInboxItem;
import com.vspicy.notification.dto.UnreadCountResponse;
import com.vspicy.notification.service.NotificationService;
import com.vspicy.notification.service.NotificationSseService;
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
    public SseEmitter stream(
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request
    ) {
        return sseService.connect(resolveUserId(userId, request));
    }

    @PostMapping("/system")
    public Result<Long> publishSystem(
            @RequestBody NotificationCreateCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(notificationService.publishSystem(command, currentUserId(request)));
    }

    @GetMapping("/inbox")
    public Result<List<NotificationInboxItem>> inbox(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "readStatus", required = false) Integer readStatus,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit,
            HttpServletRequest request
    ) {
        return Result.ok(notificationService.inbox(resolveUserId(userId, request), readStatus, limit));
    }

    @GetMapping("/unread-count")
    public Result<UnreadCountResponse> unreadCount(
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request
    ) {
        return Result.ok(notificationService.unreadCount(resolveUserId(userId, request)));
    }

    @PostMapping("/inbox/{inboxId}/read")
    public Result<String> markRead(
            @PathVariable("inboxId") Long inboxId,
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request
    ) {
        notificationService.markRead(resolveUserId(userId, request), inboxId);
        return Result.ok("ok");
    }

    @PostMapping("/inbox/read-all")
    public Result<String> markAllRead(
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request
    ) {
        notificationService.markAllRead(resolveUserId(userId, request));
        return Result.ok("ok");
    }

    @DeleteMapping("/inbox/{inboxId}")
    public Result<String> deleteInbox(
            @PathVariable("inboxId") Long inboxId,
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request
    ) {
        notificationService.deleteInbox(resolveUserId(userId, request), inboxId);
        return Result.ok("ok");
    }

    @GetMapping("/announcements")
    public Result<List<NotificationInboxItem>> announcements(
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        return Result.ok(notificationService.announcements(limit));
    }

    @GetMapping("/online-count")
    public Result<Integer> onlineCount(
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request
    ) {
        return Result.ok(sseService.onlineCount(resolveUserId(userId, request)));
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("vspicy-notification ok");
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
