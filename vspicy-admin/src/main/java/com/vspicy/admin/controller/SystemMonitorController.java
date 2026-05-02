package com.vspicy.admin.controller;

import com.vspicy.admin.dto.SystemMonitorOverviewView;
import com.vspicy.admin.service.SystemMonitorService;
import com.vspicy.common.core.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system-monitor")
public class SystemMonitorController {
    private final SystemMonitorService systemMonitorService;

    public SystemMonitorController(SystemMonitorService systemMonitorService) {
        this.systemMonitorService = systemMonitorService;
    }

    @GetMapping("/overview")
    public Result<SystemMonitorOverviewView> overview() {
        return Result.ok(systemMonitorService.overview());
    }
}
