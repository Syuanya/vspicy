package com.vspicy.notification.controller;

import com.vspicy.common.core.Result;
import com.vspicy.notification.dto.AnnouncementCommand;
import com.vspicy.notification.dto.AnnouncementOverviewView;
import com.vspicy.notification.dto.AnnouncementPublishCommand;
import com.vspicy.notification.dto.AnnouncementView;
import com.vspicy.notification.service.NotificationAnnouncementService;
import com.vspicy.notification.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications/admin/announcements")
public class NotificationAnnouncementAdminController {
    private final NotificationAnnouncementService announcementService;

    public NotificationAnnouncementAdminController(NotificationAnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/overview")
    public Result<AnnouncementOverviewView> overview() {
        return Result.ok(announcementService.overview());
    }

    @GetMapping
    public Result<List<AnnouncementView>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "pinned", required = false) Boolean pinned,
            @RequestParam(value = "onlyEffective", required = false, defaultValue = "false") Boolean onlyEffective,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(announcementService.list(status, category, priority, pinned, onlyEffective, keyword, limit));
    }

    @GetMapping("/{id}")
    public Result<AnnouncementView> get(@PathVariable("id") Long id) {
        return Result.ok(announcementService.get(id));
    }

    @PostMapping
    public Result<AnnouncementView> create(@RequestBody AnnouncementCommand command, HttpServletRequest request) {
        return Result.ok(announcementService.create(command, CurrentUser.optionalId(request)));
    }

    @PutMapping("/{id}")
    public Result<AnnouncementView> update(
            @PathVariable("id") Long id,
            @RequestBody AnnouncementCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(announcementService.update(id, command, CurrentUser.optionalId(request)));
    }

    @PostMapping("/{id}/publish")
    public Result<AnnouncementView> publish(
            @PathVariable("id") Long id,
            @RequestBody(required = false) AnnouncementPublishCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(announcementService.publish(id, command, CurrentUser.optionalId(request)));
    }

    @PostMapping("/{id}/offline")
    public Result<AnnouncementView> offline(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(announcementService.offline(id, CurrentUser.optionalId(request)));
    }

    @PostMapping("/{id}/archive")
    public Result<AnnouncementView> archive(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(announcementService.archive(id, CurrentUser.optionalId(request)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        announcementService.delete(id);
        return Result.ok();
    }
}
