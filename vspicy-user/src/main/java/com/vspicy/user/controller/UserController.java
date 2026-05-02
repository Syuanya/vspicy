package com.vspicy.user.controller;

import com.vspicy.common.core.Result;
import com.vspicy.user.dto.*;
import com.vspicy.user.entity.SysUser;
import com.vspicy.user.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/overview")
    public Result<UserOverviewView> overview() {
        return Result.ok(userService.overview());
    }

    @GetMapping("/{id}")
    public Result<SysUser> getUser(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    @GetMapping("/{id}/detail")
    public Result<UserDetailView> detail(@PathVariable Long id) {
        return Result.ok(userService.detail(id));
    }

    @GetMapping
    public Result<List<SysUser>> listUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "userType", required = false) Integer userType,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return Result.ok(userService.listUsers(keyword, status, userType, limit));
    }

    @PutMapping("/{id}")
    public Result<SysUser> updateUser(@PathVariable Long id, @RequestBody UserUpdateCommand command) {
        return Result.ok(userService.updateUser(id, command));
    }

    @PutMapping("/{id}/status")
    public Result<SysUser> updateStatus(@PathVariable Long id, @RequestBody UserStatusCommand command) {
        return Result.ok(userService.updateStatus(id, command.status()));
    }

    @PutMapping("/{id}/type")
    public Result<SysUser> updateUserType(@PathVariable Long id, @RequestBody UserTypeCommand command) {
        return Result.ok(userService.updateUserType(id, command.userType()));
    }

    @PutMapping("/{id}/password")
    public Result<SysUser> resetPassword(@PathVariable Long id, @RequestBody UserPasswordResetCommand command) {
        return Result.ok(userService.resetPassword(id, command.password()));
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("vspicy-user ok");
    }
}
