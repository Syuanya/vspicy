package com.vspicy.content.controller;

import com.vspicy.common.core.Result;
import com.vspicy.content.dto.SensitiveWordCommand;
import com.vspicy.content.dto.SensitiveWordOverviewView;
import com.vspicy.content.dto.SensitiveWordTestCommand;
import com.vspicy.content.dto.SensitiveWordTestView;
import com.vspicy.content.entity.SensitiveWord;
import com.vspicy.content.service.SensitiveWordService;
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
@RequestMapping("/api/audit/sensitive-words")
public class SensitiveWordController {
    private final SensitiveWordService service;

    public SensitiveWordController(SensitiveWordService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public Result<SensitiveWordOverviewView> overview() {
        return Result.ok(service.overview());
    }

    @GetMapping
    public Result<List<SensitiveWord>> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit
    ) {
        return Result.ok(service.list(category, riskLevel, status, keyword, limit));
    }

    @PostMapping
    public Result<SensitiveWord> create(@RequestBody SensitiveWordCommand command) {
        return Result.ok(service.create(command));
    }

    @PutMapping("/{id}")
    public Result<SensitiveWord> update(@PathVariable("id") Long id, @RequestBody SensitiveWordCommand command) {
        return Result.ok(service.update(id, command));
    }

    @PostMapping("/{id}/enable")
    public Result<SensitiveWord> enable(@PathVariable("id") Long id) {
        return Result.ok(service.enable(id));
    }

    @PostMapping("/{id}/disable")
    public Result<SensitiveWord> disable(@PathVariable("id") Long id) {
        return Result.ok(service.disable(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return Result.ok();
    }

    @PostMapping("/test")
    public Result<SensitiveWordTestView> test(@RequestBody SensitiveWordTestCommand command) {
        return Result.ok(service.test(command));
    }
}
