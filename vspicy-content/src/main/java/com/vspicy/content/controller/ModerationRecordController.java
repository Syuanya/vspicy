package com.vspicy.content.controller;

import com.vspicy.common.core.Result;
import com.vspicy.content.entity.ContentModerationRecord;
import com.vspicy.content.service.ContentModerationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit/moderation-records")
public class ModerationRecordController {
    private final ContentModerationService service;

    public ModerationRecordController(ContentModerationService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<ContentModerationRecord>> list(
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "result", required = false) String result,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(service.listRecords(bizType, result, userId, limit));
    }
}
