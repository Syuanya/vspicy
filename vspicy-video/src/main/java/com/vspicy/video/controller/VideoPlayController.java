package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.common.exception.BizException;
import com.vspicy.video.config.VideoStorageProperties;
import com.vspicy.video.entity.Video;
import com.vspicy.video.entity.VideoFile;
import com.vspicy.video.service.VideoQueryService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/videos")
public class VideoPlayController {
    private final VideoStorageProperties storageProperties;
    private final VideoQueryService videoQueryService;

    public VideoPlayController(VideoStorageProperties storageProperties, VideoQueryService videoQueryService) {
        this.storageProperties = storageProperties;
        this.videoQueryService = videoQueryService;
    }

    @GetMapping("/{videoId}/play-info")
    public Result<Map<String, Object>> playInfo(@PathVariable("videoId") Long videoId) {
        Video video = videoQueryService.getVideo(videoId);
        List<VideoFile> files = videoQueryService.getVideoFiles(videoId);

        String minioHlsUrl = files.stream()
                .filter(file -> "HLS_M3U8".equals(file.getFileType()))
                .map(VideoFile::getUrl)
                .findFirst()
                .orElse("");

        Map<String, Object> data = new HashMap<>();
        data.put("video", video);
        data.put("localHlsUrl", "/api/videos/" + videoId + "/hls/index.m3u8");
        data.put("minioHlsUrl", minioHlsUrl);
        data.put("files", files);
        return Result.ok(data);
    }

    @GetMapping("/{videoId}/hls/index.m3u8")
    public ResponseEntity<byte[]> m3u8(@PathVariable("videoId") Long videoId) {
        try {
            Path path = Path.of(storageProperties.getHlsRoot(), String.valueOf(videoId), "index.m3u8");
            if (!Files.exists(path)) {
                throw new BizException(404, "m3u8 文件不存在");
            }

            byte[] body = Files.readString(path, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl;charset=UTF-8"))
                    .cacheControl(CacheControl.noCache())
                    .body(body);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(500, "读取 m3u8 失败: " + ex.getMessage());
        }
    }

    @GetMapping("/{videoId}/hls/{fileName}")
    public ResponseEntity<Resource> hlsSegment(
            @PathVariable("videoId") Long videoId,
            @PathVariable("fileName") String fileName
    ) {
        try {
            if (!fileName.endsWith(".ts")) {
                throw new BizException(400, "非法 HLS 分片文件");
            }

            Path path = Path.of(storageProperties.getHlsRoot(), String.valueOf(videoId), fileName);
            if (!Files.exists(path)) {
                throw new BizException(404, "HLS 分片不存在");
            }

            Resource resource = new FileSystemResource(path);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/MP2T"))
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                    .contentLength(Files.size(path))
                    .body(resource);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(500, "读取 HLS 分片失败: " + ex.getMessage());
        }
    }
}
