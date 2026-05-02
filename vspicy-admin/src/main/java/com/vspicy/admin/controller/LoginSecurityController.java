package com.vspicy.admin.controller;

import com.vspicy.admin.dto.LoginLogItem;
import com.vspicy.admin.dto.LoginSecurityOverviewView;
import com.vspicy.admin.dto.OnlineSessionItem;
import com.vspicy.admin.dto.SessionKickCommand;
import com.vspicy.admin.service.LoginSecurityService;
import com.vspicy.common.core.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/login-security")
public class LoginSecurityController {
    private final LoginSecurityService loginSecurityService;

    public LoginSecurityController(LoginSecurityService loginSecurityService) {
        this.loginSecurityService = loginSecurityService;
    }

    @GetMapping("/overview")
    public Result<LoginSecurityOverviewView> overview(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(loginSecurityService.overview(days));
    }

    @GetMapping("/logs")
    public Result<List<LoginLogItem>> logs(@RequestParam(required = false) Long userId,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "100") int limit) {
        return Result.ok(loginSecurityService.loginLogs(userId, status, keyword, limit));
    }

    @GetMapping("/sessions")
    public Result<List<OnlineSessionItem>> sessions(@RequestParam(required = false) Long userId,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "100") int limit) {
        return Result.ok(loginSecurityService.sessions(userId, status, keyword, limit));
    }

    @PostMapping("/sessions/{id}/kick")
    public Result<Void> kick(@PathVariable Long id, @RequestBody(required = false) SessionKickCommand command) {
        loginSecurityService.kickSession(id, command);
        return Result.ok();
    }

    @PostMapping("/sessions/cleanup-expired")
    public Result<Integer> cleanupExpired() {
        return Result.ok(loginSecurityService.cleanupExpired());
    }
}
