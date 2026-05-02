package com.vspicy.admin.controller;

import com.vspicy.admin.audit.AuditLog;
import com.vspicy.admin.dto.MenuCommand;
import com.vspicy.admin.dto.MenuOverviewView;
import com.vspicy.admin.dto.MenuView;
import com.vspicy.admin.dto.RoleMenuAssignCommand;
import com.vspicy.admin.dto.RoleMenuSummaryView;
import com.vspicy.admin.service.MenuService;
import com.vspicy.common.core.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/menus")
public class MenuController {
    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public Result<List<MenuView>> tree(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "visible", required = false) Boolean visible
    ) {
        return Result.ok(menuService.tree(keyword, status, visible));
    }

    @GetMapping("/overview")
    public Result<MenuOverviewView> overview() {
        return Result.ok(menuService.overview());
    }

    @GetMapping("/{id}")
    public Result<MenuView> get(@PathVariable("id") Long id) {
        return Result.ok(menuService.get(id));
    }

    @AuditLog(type = "CREATE", title = "创建后台菜单")
    @PostMapping
    public Result<MenuView> create(@RequestBody MenuCommand command) {
        return Result.ok(menuService.create(command));
    }

    @AuditLog(type = "UPDATE", title = "更新后台菜单")
    @PutMapping("/{id}")
    public Result<MenuView> update(@PathVariable("id") Long id, @RequestBody MenuCommand command) {
        return Result.ok(menuService.update(id, command));
    }

    @AuditLog(type = "UPDATE", title = "启用后台菜单")
    @PostMapping("/{id}/enable")
    public Result<MenuView> enable(@PathVariable("id") Long id) {
        return Result.ok(menuService.enable(id));
    }

    @AuditLog(type = "UPDATE", title = "停用后台菜单")
    @PostMapping("/{id}/disable")
    public Result<MenuView> disable(@PathVariable("id") Long id) {
        return Result.ok(menuService.disable(id));
    }

    @AuditLog(type = "UPDATE", title = "显示后台菜单")
    @PostMapping("/{id}/show")
    public Result<MenuView> show(@PathVariable("id") Long id) {
        return Result.ok(menuService.show(id));
    }

    @AuditLog(type = "UPDATE", title = "隐藏后台菜单")
    @PostMapping("/{id}/hide")
    public Result<MenuView> hide(@PathVariable("id") Long id) {
        return Result.ok(menuService.hide(id));
    }

    @AuditLog(type = "DELETE", title = "删除后台菜单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        menuService.delete(id);
        return Result.ok();
    }

    @GetMapping("/roles/{roleId}")
    public Result<RoleMenuSummaryView> roleMenus(@PathVariable("roleId") Long roleId) {
        return Result.ok(menuService.roleMenus(roleId));
    }

    @AuditLog(type = "UPDATE", title = "分配角色菜单")
    @PostMapping("/roles/{roleId}")
    public Result<RoleMenuSummaryView> assignRoleMenus(@PathVariable("roleId") Long roleId,
                                                       @RequestBody RoleMenuAssignCommand command) {
        return Result.ok(menuService.assignRoleMenus(roleId, command));
    }
}
