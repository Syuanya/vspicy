package com.vspicy.admin.controller;

import com.vspicy.admin.audit.AuditLog;
import com.vspicy.admin.dto.SystemDictItemCommand;
import com.vspicy.admin.dto.SystemDictItemMoveCommand;
import com.vspicy.admin.dto.SystemDictItemView;
import com.vspicy.admin.dto.SystemDictOverviewView;
import com.vspicy.admin.dto.SystemDictTypeCommand;
import com.vspicy.admin.dto.SystemDictTypeView;
import com.vspicy.admin.service.AdminSystemDictService;
import com.vspicy.common.core.Result;
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
@RequestMapping("/api/admin/system-dicts")
public class AdminSystemDictController {
    private final AdminSystemDictService systemDictService;

    public AdminSystemDictController(AdminSystemDictService systemDictService) {
        this.systemDictService = systemDictService;
    }

    @GetMapping("/overview")
    public Result<SystemDictOverviewView> overview() {
        return Result.ok(systemDictService.overview());
    }

    @GetMapping("/types")
    public Result<List<SystemDictTypeView>> listTypes(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(systemDictService.listTypes(keyword, status, limit));
    }

    @GetMapping("/types/{id}")
    public Result<SystemDictTypeView> getType(@PathVariable("id") Long id) {
        return Result.ok(systemDictService.getType(id));
    }

    @AuditLog(type = "CREATE", title = "创建系统字典类型")
    @PostMapping("/types")
    public Result<SystemDictTypeView> createType(@RequestBody SystemDictTypeCommand command) {
        return Result.ok(systemDictService.createType(command));
    }

    @AuditLog(type = "UPDATE", title = "更新系统字典类型")
    @PutMapping("/types/{id}")
    public Result<SystemDictTypeView> updateType(@PathVariable("id") Long id, @RequestBody SystemDictTypeCommand command) {
        return Result.ok(systemDictService.updateType(id, command));
    }

    @AuditLog(type = "UPDATE", title = "启用系统字典类型")
    @PostMapping("/types/{id}/enable")
    public Result<SystemDictTypeView> enableType(@PathVariable("id") Long id) {
        return Result.ok(systemDictService.enableType(id));
    }

    @AuditLog(type = "UPDATE", title = "停用系统字典类型")
    @PostMapping("/types/{id}/disable")
    public Result<SystemDictTypeView> disableType(@PathVariable("id") Long id) {
        return Result.ok(systemDictService.disableType(id));
    }

    @AuditLog(type = "DELETE", title = "删除系统字典类型")
    @DeleteMapping("/types/{id}")
    public Result<Void> deleteType(@PathVariable("id") Long id) {
        systemDictService.deleteType(id);
        return Result.ok();
    }

    @GetMapping("/items")
    public Result<List<SystemDictItemView>> listItems(
            @RequestParam(value = "typeCode", required = false) String typeCode,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit
    ) {
        return Result.ok(systemDictService.listItems(typeCode, keyword, status, limit));
    }

    @GetMapping("/types/{typeCode}/items")
    public Result<List<SystemDictItemView>> listItemsByType(
            @PathVariable("typeCode") String typeCode,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit
    ) {
        return Result.ok(systemDictService.listItems(typeCode, null, status, limit));
    }

    @GetMapping("/items/{id}")
    public Result<SystemDictItemView> getItem(@PathVariable("id") Long id) {
        return Result.ok(systemDictService.getItem(id));
    }

    @AuditLog(type = "CREATE", title = "创建系统字典项")
    @PostMapping("/items")
    public Result<SystemDictItemView> createItem(@RequestBody SystemDictItemCommand command) {
        return Result.ok(systemDictService.createItem(command));
    }

    @AuditLog(type = "UPDATE", title = "更新系统字典项")
    @PutMapping("/items/{id}")
    public Result<SystemDictItemView> updateItem(@PathVariable("id") Long id, @RequestBody SystemDictItemCommand command) {
        return Result.ok(systemDictService.updateItem(id, command));
    }

    @AuditLog(type = "UPDATE", title = "启用系统字典项")
    @PostMapping("/items/{id}/enable")
    public Result<SystemDictItemView> enableItem(@PathVariable("id") Long id) {
        return Result.ok(systemDictService.enableItem(id));
    }

    @AuditLog(type = "UPDATE", title = "停用系统字典项")
    @PostMapping("/items/{id}/disable")
    public Result<SystemDictItemView> disableItem(@PathVariable("id") Long id) {
        return Result.ok(systemDictService.disableItem(id));
    }

    @AuditLog(type = "UPDATE", title = "移动系统字典项")
    @PostMapping("/items/{id}/move")
    public Result<SystemDictItemView> moveItem(@PathVariable("id") Long id, @RequestBody SystemDictItemMoveCommand command) {
        return Result.ok(systemDictService.moveItem(id, command));
    }

    @AuditLog(type = "DELETE", title = "删除系统字典项")
    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable("id") Long id) {
        systemDictService.deleteItem(id);
        return Result.ok();
    }
}
