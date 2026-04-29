package com.vspicy.auth.controller;

import com.vspicy.auth.dto.CurrentUserResponse;
import com.vspicy.auth.dto.LoginCommand;
import com.vspicy.auth.dto.LoginResponse;
import com.vspicy.auth.dto.ProfileUpdateCommand;
import com.vspicy.auth.dto.RegisterCommand;
import com.vspicy.auth.service.AuthService;
import com.vspicy.common.core.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginCommand command) {
        return Result.ok(authService.login(command));
    }

    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody RegisterCommand command) {
        return Result.ok(authService.register(command));
    }

    @GetMapping("/me")
    public Result<CurrentUserResponse> me(HttpServletRequest request) {
        String userId = resolveUserId(request);
        if (userId == null || userId.isBlank()) {
            return Result.ok(authService.currentUser(null));
        }
        return Result.ok(authService.currentUser(Long.valueOf(userId)));
    }

    @PutMapping("/profile")
    public Result<CurrentUserResponse> updateProfile(HttpServletRequest request, @RequestBody ProfileUpdateCommand command) {
        String userId = resolveUserId(request);
        if (userId == null || userId.isBlank()) {
            return Result.ok(authService.updateProfile(null, command));
        }
        return Result.ok(authService.updateProfile(Long.valueOf(userId), command));
    }

    @GetMapping("/dev-token")
    public Result<LoginResponse> devToken() {
        return Result.ok(authService.devToken());
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("vspicy-auth ok");
    }

    private String resolveUserId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        Long tokenUserId = authService.resolveUserIdFromToken(request.getHeader("Authorization"));
        return tokenUserId == null ? null : String.valueOf(tokenUserId);
    }
}
