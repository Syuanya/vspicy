package com.vspicy.admin.controller;

import com.vspicy.admin.audit.AuditLog;
import com.vspicy.admin.dto.*;
import com.vspicy.admin.entity.SysPermission;
import com.vspicy.admin.entity.SysRole;
import com.vspicy.admin.service.AdminPermissionService;
import com.vspicy.common.core.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminPermissionController {
    private final AdminPermissionService service;

    public AdminPermissionController(AdminPermissionService service) {
        this.service = service;
    }

    @GetMapping("/roles")
    public Result<List<SysRole>> listRoles() {
        return Result.ok(service.listRoles());
    }

    @AuditLog(type = "CREATE", title = "创建角色")
    @PostMapping("/roles")
    public Result<SysRole> createRole(@RequestBody RoleCommand command) {
        return Result.ok(service.createRole(command));
    }

    @GetMapping("/permissions")
    public Result<List<SysPermission>> listPermissions(@RequestParam(value = "type", required = false) String type) {
        return Result.ok(service.listPermissions(type));
    }

    @AuditLog(type = "CREATE", title = "创建权限")
    @PostMapping("/permissions")
    public Result<SysPermission> createPermission(@RequestBody PermissionCommand command) {
        return Result.ok(service.createPermission(command));
    }

    @GetMapping("/users/{userId}/roles")
    public Result<List<SysRole>> userRoles(@PathVariable("userId") Long userId) {
        return Result.ok(service.userRoles(userId));
    }

    @AuditLog(type = "ASSIGN", title = "分配用户角色")
    @PostMapping("/users/{userId}/roles")
    public Result<List<SysRole>> assignUserRoles(
            @PathVariable("userId") Long userId,
            @RequestBody AssignRolesCommand command
    ) {
        return Result.ok(service.assignUserRoles(userId, command));
    }

    @GetMapping("/roles/{roleId}/permissions")
    public Result<List<SysPermission>> rolePermissions(@PathVariable("roleId") Long roleId) {
        return Result.ok(service.rolePermissions(roleId));
    }

    @AuditLog(type = "ASSIGN", title = "分配角色权限")
    @PostMapping("/roles/{roleId}/permissions")
    public Result<List<SysPermission>> assignRolePermissions(
            @PathVariable("roleId") Long roleId,
            @RequestBody AssignPermissionsCommand command
    ) {
        return Result.ok(service.assignRolePermissions(roleId, command));
    }

    @GetMapping("/users/{userId}/permission-view")
    public Result<UserPermissionView> permissionView(@PathVariable("userId") Long userId) {
        return Result.ok(service.permissionView(userId));
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("vspicy-admin ok");
    }
}
