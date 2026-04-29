package com.vspicy.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.video.config.MinioProperties;
import com.vspicy.video.config.VideoStorageProperties;
import com.vspicy.video.entity.Video;
import com.vspicy.video.entity.VideoFile;
import com.vspicy.video.entity.VideoTranscodeTask;
import com.vspicy.video.mapper.VideoFileMapper;
import com.vspicy.video.mapper.VideoMapper;
import com.vspicy.video.mapper.VideoTranscodeTaskMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class VideoTranscodeService {
    private final ThreadPoolTaskExecutor videoTranscodeExecutor;
    private final VideoTranscodeTaskMapper transcodeTaskMapper;
    private final VideoMapper videoMapper;
    private final VideoFileMapper videoFileMapper;
    private final VideoStorageProperties storageProperties;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public VideoTranscodeService(
            ThreadPoolTaskExecutor videoTranscodeExecutor,
            VideoTranscodeTaskMapper transcodeTaskMapper,
            VideoMapper videoMapper,
            VideoFileMapper videoFileMapper,
            VideoStorageProperties storageProperties,
            MinioClient minioClient,
            MinioProperties minioProperties
    ) {
        this.videoTranscodeExecutor = videoTranscodeExecutor;
        this.transcodeTaskMapper = transcodeTaskMapper;
        this.videoMapper = videoMapper;
        this.videoFileMapper = videoFileMapper;
        this.storageProperties = storageProperties;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    public void submitLocal(Long transcodeTaskId) {
        videoTranscodeExecutor.execute(() -> runTranscode(transcodeTaskId));
    }

    public void runTranscode(Long transcodeTaskId) {
        VideoTranscodeTask task = transcodeTaskMapper.selectById(transcodeTaskId);
        if (task == null) {
            return;
        }

        if ("SUCCESS".equals(task.getStatus())) {
            return;
        }

        Video video = videoMapper.selectById(task.getVideoId());
        if (video == null) {
            markFailed(task, null, "视频不存在");
            return;
        }

        try {
            task.setStatus("RUNNING");
            transcodeTaskMapper.updateById(task);

            video.setStatus("TRANSCODING");
            videoMapper.updateById(video);

            Path originPath = Path.of(task.getSourceFilePath());
            if (!Files.exists(originPath)) {
                throw new BizException(404, "源视频文件不存在: " + originPath);
            }

            Path hlsDir = transcodeToHls(video.getId(), originPath);
            String m3u8ObjectKey = uploadHlsDirectory(video.getId(), hlsDir);

            task.setStatus("SUCCESS");
            task.setErrorMessage(null);
            transcodeTaskMapper.updateById(task);

            video.setStatus("PUBLISHED");
            videoMapper.updateById(video);

            System.out.println("视频转码完成 videoId=" + video.getId() + ", m3u8=" + m3u8ObjectKey);
        } catch (Exception ex) {
            markFailed(task, video, ex.getMessage());
        }
    }

    private Path transcodeToHls(Long videoId, Path originPath) throws Exception {
        Path hlsDir = Path.of(storageProperties.getHlsRoot(), String.valueOf(videoId));
        Files.createDirectories(hlsDir);

        Path m3u8Path = hlsDir.resolve("index.m3u8");
        Path segmentPattern = hlsDir.resolve("seg_%05d.ts");

        ProcessBuilder builder = new ProcessBuilder(
                storageProperties.getFfmpegPath(),
                "-y",
                "-i", originPath.toString(),
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-c:a", "aac",
                "-f", "hls",
                "-hls_time", "6",
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", segmentPattern.toString(),
                m3u8Path.toString()
        );

        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new BizException(500, "FFmpeg 转码失败: " + output);
        }
        if (!Files.exists(m3u8Path)) {
            throw new BizException(500, "FFmpeg 未生成 m3u8 文件");
        }

        return hlsDir;
    }

    private String uploadHlsDirectory(Long videoId, Path hlsDir) throws Exception {
        String m3u8ObjectKey = null;

        try (var stream = Files.list(hlsDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();

            for (Path file : files) {
                String fileName = file.getFileName().toString();
                String objectKey = "videos/%s/hls/%s".formatted(videoId, fileName);
                String contentType = fileName.endsWith(".m3u8")
                        ? "application/vnd.apple.mpegurl"
                        : "video/MP2T";

                try (InputStream inputStream = Files.newInputStream(file)) {
                    minioClient.putObject(PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .contentType(contentType)
                            .stream(inputStream, Files.size(file), -1)
                            .build());
                }

                String checksum;
                try (InputStream inputStream = Files.newInputStream(file)) {
                    checksum = DigestUtils.sha256Hex(inputStream);
                }

                String fileType = fileName.endsWith(".m3u8") ? "HLS_M3U8" : "HLS_TS";
                String url = minioProperties.getEndpoint() + "/" + minioProperties.getBucket() + "/" + objectKey;
                saveVideoFile(videoId, fileType, "MINIO", minioProperties.getBucket(), objectKey, url, Files.size(file), checksum);

                if (fileName.endsWith(".m3u8")) {
                    m3u8ObjectKey = objectKey;
                }
            }
        }

        if (m3u8ObjectKey == null) {
            throw new BizException(500, "未找到 m3u8 文件");
        }

        return m3u8ObjectKey;
    }

    private void saveVideoFile(Long videoId, String fileType, String storageType, String bucket, String objectKey, String url, Long sizeBytes, String checksum) {
        Long existed = videoFileMapper.selectCount(new LambdaQueryWrapper<VideoFile>()
                .eq(VideoFile::getVideoId, videoId)
                .eq(VideoFile::getFileType, fileType)
                .eq(VideoFile::getObjectKey, objectKey));

        if (existed != null && existed > 0) {
            return;
        }

        VideoFile file = new VideoFile();
        file.setVideoId(videoId);
        file.setFileType(fileType);
        file.setStorageType(storageType);
        file.setBucket(bucket);
        file.setObjectKey(objectKey);
        file.setUrl(url);
        file.setSizeBytes(sizeBytes);
        file.setChecksum(checksum);
        videoFileMapper.insert(file);
    }

    private void markFailed(VideoTranscodeTask task, Video video, String reason) {
        if (task != null) {
            task.setStatus("FAILED");
            task.setErrorMessage(reason == null ? "未知错误" : reason);
            transcodeTaskMapper.updateById(task);
        }
        if (video != null) {
            video.setStatus("TRANSCODE_FAILED");
            videoMapper.updateById(video);
        }
        System.err.println("视频转码失败: " + reason);
    }
}
