package com.vspicy.member.controller;

import com.vspicy.common.core.Result;
import com.vspicy.member.dto.*;
import com.vspicy.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/plans")
    public Result<List<MemberPlanView>> plans() {
        return Result.ok(memberService.plans());
    }

    @GetMapping("/me")
    public Result<UserMembershipView> me(
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request
    ) {
        return Result.ok(memberService.current(resolveUserId(userId, request)));
    }

    @PostMapping("/subscribe")
    public Result<UserMembershipView> subscribe(
            @RequestBody SubscribeCommand command,
            HttpServletRequest request
    ) {
        return Result.ok(memberService.subscribe(currentUserId(request), command));
    }

    @PostMapping("/cancel")
    public Result<UserMembershipView> cancel(
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request
    ) {
        return Result.ok(memberService.cancel(resolveUserId(userId, request)));
    }

    @GetMapping("/benefits")
    public Result<List<MemberBenefitView>> benefits(
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request
    ) {
        return Result.ok(memberService.benefits(resolveUserId(userId, request)));
    }

    @GetMapping("/check/hd")
    public Result<MemberCheckResponse> checkHd(
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request
    ) {
        return Result.ok(memberService.checkHd(resolveUserId(userId, request)));
    }

    @GetMapping("/check/upload")
    public Result<MemberCheckResponse> checkUpload(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "sizeMb", required = false, defaultValue = "0") Long sizeMb,
            HttpServletRequest request
    ) {
        return Result.ok(memberService.checkUpload(resolveUserId(userId, request), sizeMb));
    }

    @PostMapping("/cache/refresh")
    public Result<UserMembershipView> refreshCache(
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request
    ) {
        return Result.ok(memberService.refreshCache(resolveUserId(userId, request)));
    }

    @DeleteMapping("/cache")
    public Result<String> evictCache(
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request
    ) {
        memberService.evictCache(resolveUserId(userId, request));
        return Result.ok("ok");
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("vspicy-member ok");
    }

    private Long resolveUserId(Long userId, HttpServletRequest request) {
        if (userId != null) {
            return userId;
        }
        Long headerUserId = currentUserId(request);
        return headerUserId == null ? 1L : headerUserId;
    }

    private Long currentUserId(HttpServletRequest request) {
        String value = request.getHeader("X-User-Id");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
