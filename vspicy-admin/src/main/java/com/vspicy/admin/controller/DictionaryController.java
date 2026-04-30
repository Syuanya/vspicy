package com.vspicy.admin.controller;

import com.vspicy.admin.audit.AuditLog;
import com.vspicy.admin.dto.DictItemCommand;
import com.vspicy.admin.dto.DictItemView;
import com.vspicy.admin.dto.DictOverviewView;
import com.vspicy.admin.dto.DictTypeCommand;
import com.vspicy.admin.dto.DictTypeView;
import com.vspicy.admin.service.DictionaryService;
import com.vspicy.common.core.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dictionaries")
public class DictionaryController {
    private final DictionaryService dictionaryService;

    public DictionaryController(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    @GetMapping("/overview")
    public Result<DictOverviewView> overview() {
        return Result.ok(dictionaryService.overview());
    }

    @GetMapping("/types")
    public Result<List<DictTypeView>> listTypes(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        return Result.ok(dictionaryService.listTypes(keyword, status, limit));
    }

    @GetMapping("/types/{id}")
    public Result<DictTypeView> getType(@PathVariable("id") Long id) {
        return Result.ok(dictionaryService.getType(id));
    }

    @AuditLog(type = "CREATE", title = "创建字典类型")
    @PostMapping("/types")
    public Result<DictTypeView> createType(@RequestBody DictTypeCommand command) {
        return Result.ok(dictionaryService.createType(command));
    }

    @AuditLog(type = "UPDATE", title = "更新字典类型")
    @PutMapping("/types/{id}")
    public Result<DictTypeView> updateType(@PathVariable("id") Long id, @RequestBody DictTypeCommand command) {
        return Result.ok(dictionaryService.updateType(id, command));
    }

    @AuditLog(type = "UPDATE", title = "启用字典类型")
    @PostMapping("/types/{id}/enable")
    public Result<DictTypeView> enableType(@PathVariable("id") Long id) {
        return Result.ok(dictionaryService.enableType(id));
    }

    @AuditLog(type = "UPDATE", title = "停用字典类型")
    @PostMapping("/types/{id}/disable")
    public Result<DictTypeView> disableType(@PathVariable("id") Long id) {
        return Result.ok(dictionaryService.disableType(id));
    }

    @AuditLog(type = "DELETE", title = "删除字典类型")
    @DeleteMapping("/types/{id}")
    public Result<Void> deleteType(@PathVariable("id") Long id) {
        dictionaryService.deleteType(id);
        return Result.ok();
    }

    @GetMapping("/items")
    public Result<List<DictItemView>> listItems(
            @RequestParam(value = "typeCode", required = false) String typeCode,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit
    ) {
        return Result.ok(dictionaryService.listItems(typeCode, keyword, status, limit));
    }

    @GetMapping("/types/{typeCode}/items")
    public Result<List<DictItemView>> listItemsByType(
            @PathVariable("typeCode") String typeCode,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit
    ) {
        return Result.ok(dictionaryService.listItems(typeCode, null, status, limit));
    }

    @GetMapping("/items/{id}")
    public Result<DictItemView> getItem(@PathVariable("id") Long id) {
        return Result.ok(dictionaryService.getItem(id));
    }

    @AuditLog(type = "CREATE", title = "创建字典项")
    @PostMapping("/items")
    public Result<DictItemView> createItem(@RequestBody DictItemCommand command) {
        return Result.ok(dictionaryService.createItem(command));
    }

    @AuditLog(type = "UPDATE", title = "更新字典项")
    @PutMapping("/items/{id}")
    public Result<DictItemView> updateItem(@PathVariable("id") Long id, @RequestBody DictItemCommand command) {
        return Result.ok(dictionaryService.updateItem(id, command));
    }

    @AuditLog(type = "UPDATE", title = "启用字典项")
    @PostMapping("/items/{id}/enable")
    public Result<DictItemView> enableItem(@PathVariable("id") Long id) {
        return Result.ok(dictionaryService.enableItem(id));
    }

    @AuditLog(type = "UPDATE", title = "停用字典项")
    @PostMapping("/items/{id}/disable")
    public Result<DictItemView> disableItem(@PathVariable("id") Long id) {
        return Result.ok(dictionaryService.disableItem(id));
    }

    @AuditLog(type = "DELETE", title = "删除字典项")
    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable("id") Long id) {
        dictionaryService.deleteItem(id);
        return Result.ok();
    }
}
