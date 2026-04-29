package com.vspicy.profile.controller;

import com.vspicy.common.core.Result;
import com.vspicy.profile.dto.*;
import com.vspicy.profile.service.ProfileService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping("/users/{userId}/rebuild")
    public Result<ProfileBuildResponse> rebuildUserProfile(@PathVariable("userId") Long userId) {
        return Result.ok(profileService.rebuildUserProfile(userId));
    }

    @GetMapping("/users/{userId}/interests")
    public Result<List<UserInterestItem>> userInterests(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "limit", required = false, defaultValue = "30") Integer limit
    ) {
        return Result.ok(profileService.userInterests(userId, limit));
    }

    @PostMapping("/content-tags")
    public Result<ContentProfileResponse> bindContentTags(@RequestBody ContentTagCommand command) {
        return Result.ok(profileService.bindContentTags(command));
    }

    @GetMapping("/content/{targetType}/{targetId}")
    public Result<ContentProfileResponse> contentProfile(
            @PathVariable("targetType") String targetType,
            @PathVariable("targetId") Long targetId
    ) {
        return Result.ok(profileService.contentProfile(targetType, targetId));
    }

    @GetMapping("/hot-tags")
    public Result<List<HotTagResponse>> hotTags(
            @RequestParam(value = "limit", required = false, defaultValue = "30") Integer limit
    ) {
        return Result.ok(profileService.hotTags(limit));
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("vspicy-profile ok");
    }
}
