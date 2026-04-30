package com.vspicy.notification.controller;

import com.vspicy.common.core.Result;
import com.vspicy.notification.dto.NotificationPreferenceItem;
import com.vspicy.notification.dto.NotificationPreferenceSaveCommand;
import com.vspicy.notification.service.NotificationPreferenceService;
import com.vspicy.notification.web.CurrentUser;
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
    public Result<List<NotificationPreferenceItem>> list(HttpServletRequest request) {
        return Result.ok(preferenceService.list(CurrentUser.id(request)));
    }

    @PutMapping
    public Result<List<NotificationPreferenceItem>> save(
            @RequestBody NotificationPreferenceSaveCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(preferenceService.save(command, CurrentUser.id(request)));
    }
}
