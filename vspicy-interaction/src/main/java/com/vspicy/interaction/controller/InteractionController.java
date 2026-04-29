package com.vspicy.interaction.controller;

import com.vspicy.common.core.Result;
import com.vspicy.interaction.dto.*;
import com.vspicy.interaction.entity.Comment;
import com.vspicy.interaction.entity.UserBehaviorLog;
import com.vspicy.interaction.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interactions")
public class InteractionController {
    private final CommentService commentService;
    private final InteractionService interactionService;
    private final BehaviorService behaviorService;
    private final AnalyticsService analyticsService;

    public InteractionController(
            CommentService commentService,
            InteractionService interactionService,
            BehaviorService behaviorService,
            AnalyticsService analyticsService
    ) {
        this.commentService = commentService;
        this.interactionService = interactionService;
        this.behaviorService = behaviorService;
        this.analyticsService = analyticsService;
    }

    @PostMapping("/comments")
    public Result<Comment> createComment(@RequestBody CommentCreateCommand command, HttpServletRequest request) {
        return Result.ok(commentService.create(command, request));
    }

    @GetMapping("/comments")
    public Result<List<Comment>> listComments(
            @RequestParam("contentId") Long contentId,
            @RequestParam("contentType") String contentType,
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit
    ) {
        return Result.ok(commentService.list(contentId, contentType, parentId, limit));
    }

    @PostMapping("/likes/toggle")
    public Result<InteractionStatusResponse> toggleLike(@RequestBody InteractionToggleCommand command, HttpServletRequest request) {
        return Result.ok(interactionService.toggleLike(command, request));
    }

    @GetMapping("/likes/status")
    public Result<InteractionStatusResponse> likeStatus(
            @RequestParam(value = "userId", required = false, defaultValue = "1") Long userId,
            @RequestParam("targetId") Long targetId,
            @RequestParam("targetType") String targetType
    ) {
        return Result.ok(interactionService.status(userId, targetId, targetType));
    }

    @PostMapping("/favorites/toggle")
    public Result<InteractionStatusResponse> toggleFavorite(@RequestBody InteractionToggleCommand command, HttpServletRequest request) {
        return Result.ok(interactionService.toggleFavorite(command, request));
    }

    @GetMapping("/favorites/status")
    public Result<InteractionStatusResponse> favoriteStatus(
            @RequestParam(value = "userId", required = false, defaultValue = "1") Long userId,
            @RequestParam("targetId") Long targetId,
            @RequestParam("targetType") String targetType
    ) {
        return Result.ok(interactionService.status(userId, targetId, targetType));
    }

    @PostMapping("/behaviors")
    public Result<UserBehaviorLog> recordBehavior(@RequestBody BehaviorLogCommand command, HttpServletRequest request) {
        return Result.ok(behaviorService.record(command, request));
    }

    @GetMapping("/analytics/hot-content")
    public Result<List<HotContentResponse>> hotContent(
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        return Result.ok(analyticsService.hotContent(targetType, limit));
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("vspicy-interaction ok");
    }
}
