package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.AdminServiceHealthSummaryView;
import com.vspicy.video.service.AdminServiceHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos/admin/service-health")
public class AdminServiceHealthController {
    private final AdminServiceHealthService serviceHealthService;

    public AdminServiceHealthController(AdminServiceHealthService serviceHealthService) {
        this.serviceHealthService = serviceHealthService;
    }

    @GetMapping("/summary")
    public Result<AdminServiceHealthSummaryView> summary() {
        return Result.ok(serviceHealthService.summary());
    }
}
