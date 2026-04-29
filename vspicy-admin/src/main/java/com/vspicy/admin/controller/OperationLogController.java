package com.vspicy.admin.controller;

import com.vspicy.admin.entity.OperationLog;
import com.vspicy.admin.service.OperationLogService;
import com.vspicy.common.core.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/operation-logs")
public class OperationLogController {
    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public Result<List<OperationLog>> list(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "operationType", required = false) String operationType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(operationLogService.list(userId, operationType, status, limit));
    }
}
