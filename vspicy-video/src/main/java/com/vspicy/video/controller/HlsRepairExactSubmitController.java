package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.service.HlsRepairTranscodeSubmitService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 可选手动测试接口。
 *
 * 正常情况下仍建议走：
 * POST /api/videos/upload/storage/hls-repair/execute/{id}
 *
 * 这个接口用于绕过 Phase49 执行器，直接验证精确适配服务能否提交现有转码任务。
 */
@RestController
@RequestMapping("/api/videos/upload/storage/hls-repair/exact-submit")
public class HlsRepairExactSubmitController {
    private final HlsRepairTranscodeSubmitService submitService;

    public HlsRepairExactSubmitController(HlsRepairTranscodeSubmitService submitService) {
        this.submitService = submitService;
    }

    @PostMapping("/{id}")
    public Result<String> submit(@PathVariable Long id) {
        submitService.submitRepairTranscodeTask(Map.of("taskId", id));
        return Result.ok("已提交现有 VideoTranscodeService.submitLocal");
    }
}
