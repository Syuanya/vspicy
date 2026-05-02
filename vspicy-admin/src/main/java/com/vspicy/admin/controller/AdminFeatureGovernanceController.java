package com.vspicy.admin.controller;

import com.vspicy.admin.dto.FeatureCheckIssueView;
import com.vspicy.admin.dto.FeatureCheckRunView;
import com.vspicy.admin.dto.FeatureGovernanceOverviewView;
import com.vspicy.admin.dto.FeatureIssueHandleCommand;
import com.vspicy.admin.dto.FeatureRegistryCommand;
import com.vspicy.admin.dto.FeatureRegistryView;
import com.vspicy.admin.service.FeatureGovernanceService;
import com.vspicy.common.core.Result;
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
@RequestMapping("/api/admin/feature-governance")
public class AdminFeatureGovernanceController {
    private final FeatureGovernanceService featureGovernanceService;

    public AdminFeatureGovernanceController(FeatureGovernanceService featureGovernanceService) {
        this.featureGovernanceService = featureGovernanceService;
    }

    @GetMapping("/overview")
    public Result<FeatureGovernanceOverviewView> overview() {
        return Result.ok(featureGovernanceService.overview());
    }

    @GetMapping("/features")
    public Result<List<FeatureRegistryView>> listFeatures(
            @RequestParam(value = "moduleName", required = false) String moduleName,
            @RequestParam(value = "featureType", required = false) String featureType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit
    ) {
        return Result.ok(featureGovernanceService.listFeatures(moduleName, featureType, status, riskLevel, keyword, limit));
    }

    @GetMapping("/features/{id}")
    public Result<FeatureRegistryView> getFeature(@PathVariable("id") Long id) {
        return Result.ok(featureGovernanceService.getFeature(id));
    }

    @PostMapping("/features")
    public Result<FeatureRegistryView> createFeature(@RequestBody FeatureRegistryCommand command, HttpServletRequest request) {
        return Result.ok(featureGovernanceService.createFeature(command, operatorId(request)));
    }

    @PutMapping("/features/{id}")
    public Result<FeatureRegistryView> updateFeature(
            @PathVariable("id") Long id,
            @RequestBody FeatureRegistryCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(featureGovernanceService.updateFeature(id, command, operatorId(request)));
    }

    @PostMapping("/features/{id}/enable")
    public Result<FeatureRegistryView> enableFeature(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(featureGovernanceService.enableFeature(id, operatorId(request)));
    }

    @PostMapping("/features/{id}/disable")
    public Result<FeatureRegistryView> disableFeature(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(featureGovernanceService.disableFeature(id, operatorId(request)));
    }

    @DeleteMapping("/features/{id}")
    public Result<Void> deleteFeature(@PathVariable("id") Long id, HttpServletRequest request) {
        featureGovernanceService.deleteFeature(id, operatorId(request));
        return Result.ok();
    }

    @PostMapping("/checks/run")
    public Result<FeatureCheckRunView> runCheck(HttpServletRequest request) {
        return Result.ok(featureGovernanceService.runCheck(operatorId(request)));
    }

    @GetMapping("/issues")
    public Result<List<FeatureCheckIssueView>> listIssues(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "issueType", required = false) String issueType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit
    ) {
        return Result.ok(featureGovernanceService.listIssues(status, severity, issueType, keyword, limit));
    }

    @PostMapping("/issues/{id}/resolve")
    public Result<FeatureCheckIssueView> resolveIssue(
            @PathVariable("id") Long id,
            @RequestBody(required = false) FeatureIssueHandleCommand command,
            HttpServletRequest request
    ) {
        String remark = command == null ? null : command.handleRemark();
        return Result.ok(featureGovernanceService.resolveIssue(id, remark, operatorId(request)));
    }

    @PostMapping("/issues/{id}/ignore")
    public Result<FeatureCheckIssueView> ignoreIssue(
            @PathVariable("id") Long id,
            @RequestBody(required = false) FeatureIssueHandleCommand command,
            HttpServletRequest request
    ) {
        String remark = command == null ? null : command.handleRemark();
        return Result.ok(featureGovernanceService.ignoreIssue(id, remark, operatorId(request)));
    }

    @PostMapping("/issues/{id}/reopen")
    public Result<FeatureCheckIssueView> reopenIssue(@PathVariable("id") Long id) {
        return Result.ok(featureGovernanceService.reopenIssue(id));
    }

    private Long operatorId(HttpServletRequest request) {
        String value = firstHeader(request, "X-User-Id", "x-user-id", "X-UserId", "x-userid");
        if (value == null || value.isBlank()) {
            return 1L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 1L;
        }
    }

    private String firstHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
