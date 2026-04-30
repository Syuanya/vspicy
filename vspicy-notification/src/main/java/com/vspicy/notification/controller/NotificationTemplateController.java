package com.vspicy.notification.controller;

import com.vspicy.common.core.Result;
import com.vspicy.notification.dto.NotificationTemplateCommand;
import com.vspicy.notification.dto.NotificationTemplateCopyCommand;
import com.vspicy.notification.dto.NotificationTemplatePreviewView;
import com.vspicy.notification.dto.NotificationTemplatePublishCheckView;
import com.vspicy.notification.dto.NotificationTemplatePublishCommand;
import com.vspicy.notification.dto.NotificationTemplatePublishLogView;
import com.vspicy.notification.dto.NotificationTemplateValidationView;
import com.vspicy.notification.dto.NotificationTemplateView;
import com.vspicy.notification.service.NotificationTemplateService;
import com.vspicy.notification.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications/templates")
public class NotificationTemplateController {
    private final NotificationTemplateService templateService;

    public NotificationTemplateController(NotificationTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public Result<List<NotificationTemplateView>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(templateService.list(keyword, enabled, limit));
    }

    @GetMapping("/publish-logs")
    public Result<List<NotificationTemplatePublishLogView>> publishLogs(
            @RequestParam(value = "templateId", required = false) Long templateId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(templateService.publishLogs(templateId, status, limit));
    }

    @GetMapping("/publish-logs/{logId}")
    public Result<NotificationTemplatePublishLogView> publishLogDetail(@PathVariable("logId") Long logId) {
        return Result.ok(templateService.publishLogDetail(logId));
    }

    @PostMapping("/publish-logs/{logId}/retry")
    public Result<Long> retryPublishLog(
            @PathVariable("logId") Long logId,
            HttpServletRequest request
    ) {
        return Result.ok(templateService.retryPublishLog(logId, CurrentUser.optionalId(request)));
    }

    @GetMapping("/{id}")
    public Result<NotificationTemplateView> detail(@PathVariable("id") Long id) {
        return Result.ok(templateService.detail(id));
    }

    @PostMapping
    public Result<Long> create(
            @RequestBody NotificationTemplateCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(templateService.create(command, CurrentUser.optionalId(request)));
    }

    @PutMapping("/{id}")
    public Result<NotificationTemplateView> update(
            @PathVariable("id") Long id,
            @RequestBody NotificationTemplateCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(templateService.update(id, command, CurrentUser.optionalId(request)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable("id") Long id,
            HttpServletRequest request
    ) {
        templateService.delete(id, CurrentUser.optionalId(request));
        return Result.ok();
    }

    @PostMapping("/{id}/copy")
    public Result<Long> copy(
            @PathVariable("id") Long id,
            @RequestBody(required = false) NotificationTemplateCopyCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(templateService.copy(id, command, CurrentUser.optionalId(request)));
    }

    @PostMapping("/{id}/validate")
    public Result<NotificationTemplateValidationView> validateVariables(
            @PathVariable("id") Long id,
            @RequestBody(required = false) NotificationTemplatePublishCommand command
    ) {
        return Result.ok(templateService.validateVariables(id, command));
    }

    @PostMapping("/{id}/preview")
    public Result<NotificationTemplatePreviewView> preview(
            @PathVariable("id") Long id,
            @RequestBody(required = false) NotificationTemplatePublishCommand command
    ) {
        return Result.ok(templateService.preview(id, command));
    }

    @PostMapping("/{id}/publish-check")
    public Result<NotificationTemplatePublishCheckView> publishCheck(
            @PathVariable("id") Long id,
            @RequestBody(required = false) NotificationTemplatePublishCommand command
    ) {
        return Result.ok(templateService.publishCheck(id, command));
    }

    @PostMapping("/{id}/publish")
    public Result<Long> publish(
            @PathVariable("id") Long id,
            @RequestBody(required = false) NotificationTemplatePublishCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(templateService.publish(id, command, CurrentUser.optionalId(request)));
    }
}
