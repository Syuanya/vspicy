package com.vspicy.content.controller;

import com.vspicy.common.core.Result;
import com.vspicy.content.dto.SensitiveWordCommand;
import com.vspicy.content.entity.SensitiveWord;
import com.vspicy.content.service.SensitiveWordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit/sensitive-words")
public class SensitiveWordController {
    private final SensitiveWordService service;

    public SensitiveWordController(SensitiveWordService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<SensitiveWord>> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit
    ) {
        return Result.ok(service.list(category, status, limit));
    }

    @PostMapping
    public Result<SensitiveWord> create(@RequestBody SensitiveWordCommand command) {
        return Result.ok(service.create(command));
    }

    @PostMapping("/{id}/enable")
    public Result<SensitiveWord> enable(@PathVariable("id") Long id) {
        return Result.ok(service.enable(id));
    }

    @PostMapping("/{id}/disable")
    public Result<SensitiveWord> disable(@PathVariable("id") Long id) {
        return Result.ok(service.disable(id));
    }
}
