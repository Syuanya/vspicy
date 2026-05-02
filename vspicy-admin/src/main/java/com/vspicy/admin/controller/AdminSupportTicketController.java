package com.vspicy.admin.controller;

import com.vspicy.admin.dto.SupportTicketAssignCommand;
import com.vspicy.admin.dto.SupportTicketCommand;
import com.vspicy.admin.dto.SupportTicketOverviewView;
import com.vspicy.admin.dto.SupportTicketReplyCommand;
import com.vspicy.admin.dto.SupportTicketView;
import com.vspicy.admin.service.SupportTicketService;
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

/**
 * 后台工单中心。
 *
 * <p>该接口仅提供后台治理能力：运营/管理员可创建工单、分派、回复、解决、关闭、重开和删除。</p>
 */
@RestController
@RequestMapping("/api/admin/support-tickets")
public class AdminSupportTicketController {
    private final SupportTicketService supportTicketService;

    public AdminSupportTicketController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    @GetMapping("/overview")
    public Result<SupportTicketOverviewView> overview() {
        return Result.ok(supportTicketService.overview());
    }

    @GetMapping
    public Result<List<SupportTicketView>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "assigneeId", required = false) Long assigneeId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(supportTicketService.list(status, category, priority, assigneeId, keyword, limit));
    }

    @GetMapping("/{id}")
    public Result<SupportTicketView> get(@PathVariable("id") Long id) {
        return Result.ok(supportTicketService.get(id));
    }

    @PostMapping
    public Result<SupportTicketView> create(@RequestBody SupportTicketCommand command, HttpServletRequest request) {
        return Result.ok(supportTicketService.create(command, operatorId(request), operatorName(request)));
    }

    @PutMapping("/{id}")
    public Result<SupportTicketView> update(
            @PathVariable("id") Long id,
            @RequestBody SupportTicketCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(supportTicketService.update(id, command, operatorId(request), operatorName(request)));
    }

    @PostMapping("/{id}/assign")
    public Result<SupportTicketView> assign(
            @PathVariable("id") Long id,
            @RequestBody SupportTicketAssignCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(supportTicketService.assign(id, command, operatorId(request), operatorName(request)));
    }

    @PostMapping("/{id}/reply")
    public Result<SupportTicketView> reply(
            @PathVariable("id") Long id,
            @RequestBody SupportTicketReplyCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(supportTicketService.reply(id, command, operatorId(request), operatorName(request)));
    }

    @PostMapping("/{id}/resolve")
    public Result<SupportTicketView> resolve(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(supportTicketService.resolve(id, operatorId(request), operatorName(request)));
    }

    @PostMapping("/{id}/close")
    public Result<SupportTicketView> close(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(supportTicketService.close(id, operatorId(request), operatorName(request)));
    }

    @PostMapping("/{id}/reopen")
    public Result<SupportTicketView> reopen(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(supportTicketService.reopen(id, operatorId(request), operatorName(request)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        supportTicketService.delete(id);
        return Result.ok();
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

    private String operatorName(HttpServletRequest request) {
        String value = firstHeader(request, "X-Username", "x-username", "X-User-Name", "x-user-name");
        if (value == null || value.isBlank()) {
            return "dev-admin";
        }
        return value.trim();
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
