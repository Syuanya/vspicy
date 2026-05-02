package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.CreateUploadTaskCommand;
import com.vspicy.video.dto.UploadTaskResponse;
import com.vspicy.video.dto.VideoCompleteResponse;
import com.vspicy.video.dto.VideoStatusCommand;
import com.vspicy.video.entity.Video;
import com.vspicy.video.entity.VideoFile;
import com.vspicy.video.service.VideoQueryService;
import com.vspicy.video.service.VideoUploadService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/videos")
public class VideoUploadController {
    private final VideoUploadService videoUploadService;
    private final VideoQueryService videoQueryService;

    public VideoUploadController(VideoUploadService videoUploadService, VideoQueryService videoQueryService) {
        this.videoUploadService = videoUploadService;
        this.videoQueryService = videoQueryService;
    }

    @PostMapping("/upload-tasks")
    public Result<UploadTaskResponse> createUploadTask(@RequestBody CreateUploadTaskCommand command) {
        return Result.ok(videoUploadService.createTask(command));
    }

    @GetMapping("/upload-tasks/{taskId}/chunks")
    public Result<UploadTaskResponse> getUploadedChunks(@PathVariable Long taskId) {
        return Result.ok(videoUploadService.getTaskChunks(taskId));
    }

    @PostMapping("/upload-tasks/{taskId}/chunks")
    public Result<UploadTaskResponse> uploadChunk(
            @PathVariable Long taskId,
            @RequestParam(value = "chunkIndex", required = false) Integer chunkIndex,
            @RequestParam(value = "chunkHash", required = false) String chunkHash,
            @RequestParam("file") MultipartFile file
    ) {
        return Result.ok(videoUploadService.uploadChunk(taskId, chunkIndex, chunkHash, file));
    }

    @PostMapping("/upload-tasks/{taskId}/complete")
    public Result<VideoCompleteResponse> completeUpload(@PathVariable Long taskId) {
        return Result.ok(videoUploadService.completeUpload(taskId));
    }

    @GetMapping("/{videoId}")
    public Result<Video> getVideo(@PathVariable Long videoId) {
        return Result.ok(videoQueryService.getVideo(videoId));
    }

    @GetMapping
    public Result<List<Video>> listVideos(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return Result.ok(videoQueryService.listVideos(status, userId, limit));
    }

    @PutMapping("/{videoId}/status")
    public Result<Video> updateVideoStatus(@PathVariable Long videoId, @RequestBody VideoStatusCommand command) {
        return Result.ok(videoQueryService.updateStatus(videoId, command.status()));
    }

    @GetMapping("/{videoId}/files")
    public Result<List<VideoFile>> getVideoFiles(@PathVariable Long videoId) {
        return Result.ok(videoQueryService.getVideoFiles(videoId));
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("vspicy-video ok");
    }
}
