package com.vspicy.admin.controller;

import com.vspicy.admin.dto.ServiceDiagnosticsOverviewView;
import com.vspicy.admin.service.ServiceDiagnosticsService;
import com.vspicy.common.core.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/service-diagnostics")
public class ServiceDiagnosticsController {
    private final ServiceDiagnosticsService serviceDiagnosticsService;

    public ServiceDiagnosticsController(ServiceDiagnosticsService serviceDiagnosticsService) {
        this.serviceDiagnosticsService = serviceDiagnosticsService;
    }

    @GetMapping("/overview")
    public Result<ServiceDiagnosticsOverviewView> overview() {
        return Result.ok(serviceDiagnosticsService.overview());
    }
}
