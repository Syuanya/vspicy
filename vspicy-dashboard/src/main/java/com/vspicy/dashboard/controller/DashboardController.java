package com.vspicy.dashboard.controller;

import com.vspicy.common.core.Result;
import com.vspicy.dashboard.dto.ContentRankItem;
import com.vspicy.dashboard.dto.DashboardOverviewResponse;
import com.vspicy.dashboard.dto.TrendPoint;
import com.vspicy.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public Result<DashboardOverviewResponse> overview() {
        return Result.ok(dashboardService.overview());
    }

    @GetMapping("/trends")
    public Result<List<TrendPoint>> trends(
            @RequestParam(value = "days", required = false, defaultValue = "7") Integer days
    ) {
        return Result.ok(dashboardService.trends(days));
    }

    @GetMapping("/content-rank")
    public Result<List<ContentRankItem>> contentRank(
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        return Result.ok(dashboardService.contentRank(limit));
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("vspicy-dashboard ok");
    }
}
