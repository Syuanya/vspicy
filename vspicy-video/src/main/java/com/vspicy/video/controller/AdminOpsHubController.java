package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.AdminOpsHubSummaryView;
import com.vspicy.video.service.AdminOpsHubService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos/admin/ops-hub")
public class AdminOpsHubController {
    private final AdminOpsHubService opsHubService;

    public AdminOpsHubController(AdminOpsHubService opsHubService) {
        this.opsHubService = opsHubService;
    }

    @GetMapping("/summary")
    public Result<AdminOpsHubSummaryView> summary() {
        return Result.ok(opsHubService.summary());
    }
}
