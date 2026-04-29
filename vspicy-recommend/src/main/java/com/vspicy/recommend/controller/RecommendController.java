package com.vspicy.recommend.controller;

import com.vspicy.common.core.Result;
import com.vspicy.recommend.dto.*;
import com.vspicy.recommend.service.RecommendService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommend")
public class RecommendController {
    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @GetMapping("/feed")
    public Result<List<RecommendationItem>> feed(
            @RequestParam(value = "userId", required = false, defaultValue = "1") Long userId,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        return Result.ok(recommendService.feed(userId, limit));
    }

    @GetMapping("/hot")
    public Result<List<RecommendationItem>> hot(
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        return Result.ok(recommendService.hot(targetType, limit));
    }

    @GetMapping("/personalized")
    public Result<List<RecommendationItem>> personalized(
            @RequestParam(value = "userId", required = false, defaultValue = "1") Long userId,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        return Result.ok(recommendService.personalized(userId, limit));
    }

    @GetMapping("/similar")
    public Result<List<RecommendationItem>> similar(
            @RequestParam("targetId") Long targetId,
            @RequestParam("targetType") String targetType,
            @RequestParam(value = "limit", required = false, defaultValue = "12") Integer limit
    ) {
        return Result.ok(recommendService.similar(targetId, targetType, limit));
    }

    @GetMapping("/debug/users/{userId}")
    public Result<RecommendationDebugResponse> debugUser(@PathVariable("userId") Long userId) {
        return Result.ok(recommendService.debugUser(userId));
    }

    @GetMapping("/cache/stats")
    public Result<RecommendCacheStatsResponse> cacheStats() {
        return Result.ok(recommendService.cacheStats());
    }

    @DeleteMapping("/cache")
    public Result<String> clearCache() {
        long count = recommendService.clearCache();
        return Result.ok("已清理推荐缓存 key 数量: " + count);
    }

    @PostMapping("/exposures")
    public Result<String> exposure(@RequestBody RecommendationExposureCommand command) {
        recommendService.exposure(command);
        return Result.ok("ok");
    }

    @PostMapping("/feedback")
    public Result<String> feedback(@RequestBody RecommendationFeedbackCommand command) {
        recommendService.feedback(command);
        return Result.ok("ok");
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("vspicy-recommend ok");
    }
}
