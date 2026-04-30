package com.vspicy.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.video.config.MinioProperties;
import com.vspicy.video.config.VideoStorageProperties;
import com.vspicy.video.dto.CreateUploadTaskCommand;
import com.vspicy.video.dto.UploadTaskResponse;
import com.vspicy.video.dto.VideoCompleteResponse;
import com.vspicy.video.entity.*;
import com.vspicy.video.mapper.*;
import com.vspicy.video.mq.VideoTranscodeDispatcher;
import io.minio.MinioClient;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class VideoUploadService {
    private final VideoMapper videoMapper;
    private final VideoUploadTaskMapper taskMapper;
    private final VideoUploadChunkMapper chunkMapper;
    private final VideoFileMapper videoFileMapper;
    private final VideoTranscodeTaskMapper transcodeTaskMapper;
    private final VideoStorageProperties storageProperties;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final VideoTranscodeDispatcher videoTranscodeDispatcher;

    public VideoUploadService(
            VideoMapper videoMapper,
            VideoUploadTaskMapper taskMapper,
            VideoUploadChunkMapper chunkMapper,
            VideoFileMapper videoFileMapper,
            VideoTranscodeTaskMapper transcodeTaskMapper,
            VideoStorageProperties storageProperties,
            MinioClient minioClient,
            MinioProperties minioProperties,
            VideoTranscodeDispatcher videoTranscodeDispatcher
    ) {
        this.videoMapper = videoMapper;
        this.taskMapper = taskMapper;
        this.chunkMapper = chunkMapper;
        this.videoFileMapper = videoFileMapper;
        this.transcodeTaskMapper = transcodeTaskMapper;
        this.storageProperties = storageProperties;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.videoTranscodeDispatcher = videoTranscodeDispatcher;
    }

    public UploadTaskResponse createTask(CreateUploadTaskCommand command) {
        if (command.fileSize() == null || command.fileSize() <= 0) {
            throw new BizException("文件大小不合法");
        }
        if (command.chunkSize() == null || command.chunkSize() <= 0) {
            throw new BizException("分片大小不合法");
        }
        if (command.fileHash() == null || command.fileHash().isBlank()) {
            throw new BizException("文件 Hash 不能为空");
        }

        Long userId = command.userId() == null ? 1L : command.userId();

        VideoUploadTask existed = taskMapper.selectOne(new LambdaQueryWrapper<VideoUploadTask>()
                .eq(VideoUploadTask::getUserId, userId)
                .eq(VideoUploadTask::getFileHash, command.fileHash())
                .last("LIMIT 1"));

        if (existed != null) {
            return getTaskChunks(existed.getId());
        }

        Video video = new Video();
        video.setUserId(userId);
        video.setTitle(command.title() == null || command.title().isBlank() ? command.fileName() : command.title());
        video.setStatus("UPLOADING");
        video.setVisibility(1);
        videoMapper.insert(video);

        int chunkTotal = (int) Math.ceil((double) command.fileSize() / command.chunkSize());

        VideoUploadTask task = new VideoUploadTask();
        task.setUserId(userId);
        task.setVideoId(video.getId());
        task.setFileName(command.fileName());
        task.setFileHash(command.fileHash());
        task.setFileSize(command.fileSize());
        task.setChunkSize(command.chunkSize());
        task.setChunkTotal(chunkTotal);
        task.setUploadedChunks(0);
        task.setStatus("UPLOADING");
        taskMapper.insert(task);

        return getTaskChunks(task.getId());
    }

    public UploadTaskResponse uploadChunk(Long taskId, Integer chunkIndex, String chunkHash, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("分片文件不能为空");
        }
        if (chunkIndex == null || chunkIndex < 0) {
            throw new BizException("分片序号不合法");
        }

        VideoUploadTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "上传任务不存在");
        }

        VideoUploadChunk existed = chunkMapper.selectOne(new LambdaQueryWrapper<VideoUploadChunk>()
                .eq(VideoUploadChunk::getTaskId, taskId)
                .eq(VideoUploadChunk::getChunkIndex, chunkIndex)
                .last("LIMIT 1"));

        if (existed != null) {
            return getTaskChunks(taskId);
        }

        try {
            String actualHash;
            try (InputStream inputStream = file.getInputStream()) {
                actualHash = DigestUtils.sha256Hex(inputStream);
            }
            if (chunkHash != null && !chunkHash.isBlank() && !chunkHash.equalsIgnoreCase(actualHash)) {
                throw new BizException("分片 Hash 校验失败");
            }

            Path taskDir = Path.of(storageProperties.getTempRoot(), String.valueOf(taskId));
            Files.createDirectories(taskDir);
            Path chunkPath = taskDir.resolve(String.format("%06d.part", chunkIndex));
            file.transferTo(chunkPath);

            VideoUploadChunk chunk = new VideoUploadChunk();
            chunk.setTaskId(taskId);
            chunk.setChunkIndex(chunkIndex);
            chunk.setChunkHash(actualHash);
            chunk.setSizeBytes(file.getSize());
            chunk.setStoragePath(chunkPath.toString());
            chunk.setStatus("UPLOADED");
            chunkMapper.insert(chunk);

            Long count = chunkMapper.selectCount(new LambdaQueryWrapper<VideoUploadChunk>()
                    .eq(VideoUploadChunk::getTaskId, taskId));

            task.setUploadedChunks(count.intValue());
            if (count.intValue() >= task.getChunkTotal()) {
                task.setStatus("UPLOADED");
                Video video = videoMapper.selectById(task.getVideoId());
                if (video != null) {
                    video.setStatus("UPLOADED");
                    videoMapper.updateById(video);
                }
            }
            taskMapper.updateById(task);

            return getTaskChunks(taskId);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(500, "分片上传失败: " + ex.getMessage());
        }
    }

    @Transactional
    public VideoCompleteResponse completeUpload(Long taskId) {
        VideoUploadTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "上传任务不存在");
        }

        Video video = videoMapper.selectById(task.getVideoId());
        if (video == null) {
            throw new BizException(404, "视频不存在");
        }

        if ("PUBLISHED".equals(video.getStatus()) || "TRANSCODING".equals(video.getStatus())) {
            return new VideoCompleteResponse(task.getId(), video.getId(), video.getStatus(), "", "", "");
        }

        if (task.getUploadedChunks() == null || task.getUploadedChunks() < task.getChunkTotal()) {
            throw new BizException("分片未上传完成");
        }

        try {
            task.setStatus("MERGING");
            taskMapper.updateById(task);

            video.setStatus("MERGING");
            videoMapper.updateById(video);

            Path originPath = mergeChunks(task);

            String actualHash;
            try (InputStream inputStream = Files.newInputStream(originPath)) {
                actualHash = DigestUtils.sha256Hex(inputStream);
            }
            if (!task.getFileHash().equalsIgnoreCase(actualHash)) {
                throw new BizException("完整文件 Hash 校验失败");
            }

            saveVideoFile(video.getId(), "ORIGIN_LOCAL", "LOCAL", "", originPath.toString(), originPath.toString(), Files.size(originPath), actualHash);

            task.setStatus("MERGED");
            taskMapper.updateById(task);

            video.setStatus("TRANSCODING");
            videoMapper.updateById(video);

            VideoTranscodeTask transcodeTask = new VideoTranscodeTask();
            transcodeTask.setVideoId(video.getId());
            transcodeTask.setSourceFilePath(originPath.toString());
            transcodeTask.setTargetProfile("HLS_720P");
            transcodeTask.setStatus("PENDING");
            transcodeTask.setRetryCount(0);
            transcodeTaskMapper.insert(transcodeTask);

            videoTranscodeDispatcher.dispatch(transcodeTask.getId(), video.getId());

            return new VideoCompleteResponse(task.getId(), video.getId(), "TRANSCODING", originPath.toString(), "", "");
        } catch (BizException ex) {
            markFailed(task, video, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            markFailed(task, video, ex.getMessage());
            throw new BizException(500, "完成上传失败: " + ex.getMessage());
        }
    }

    private Path mergeChunks(VideoUploadTask task) throws Exception {
        Path taskDir = Path.of(storageProperties.getTempRoot(), String.valueOf(task.getId()));
        Path mergedDir = Path.of(storageProperties.getMergedRoot(), String.valueOf(task.getVideoId()));
        Files.createDirectories(mergedDir);

        String suffix = "";
        int dot = task.getFileName().lastIndexOf('.');
        if (dot >= 0) {
            suffix = task.getFileName().substring(dot);
        }

        Path originPath = mergedDir.resolve("origin" + suffix);

        List<VideoUploadChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<VideoUploadChunk>()
                        .eq(VideoUploadChunk::getTaskId, task.getId()))
                .stream()
                .sorted(Comparator.comparing(VideoUploadChunk::getChunkIndex))
                .toList();

        if (chunks.size() != task.getChunkTotal()) {
            throw new BizException("分片数量不完整");
        }

        try (OutputStream outputStream = Files.newOutputStream(originPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (int i = 0; i < task.getChunkTotal(); i++) {
                Path chunkPath = taskDir.resolve(String.format("%06d.part", i));
                if (!Files.exists(chunkPath)) {
                    throw new BizException("分片文件缺失: " + i);
                }
                Files.copy(chunkPath, outputStream);
            }
        }

        return originPath;
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

    private void markFailed(VideoUploadTask task, Video video, String reason) {
        if (task != null) {
            task.setStatus("FAILED");
            taskMapper.updateById(task);
        }
        if (video != null) {
            video.setStatus("TRANSCODE_FAILED");
            videoMapper.updateById(video);
        }
        System.err.println("视频处理失败: " + reason);
    }

    public UploadTaskResponse getTaskChunks(Long taskId) {
        VideoUploadTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "上传任务不存在");
        }

        Set<Integer> indexes = chunkMapper.selectList(new LambdaQueryWrapper<VideoUploadChunk>()
                        .eq(VideoUploadChunk::getTaskId, taskId))
                .stream()
                .map(VideoUploadChunk::getChunkIndex)
                .collect(Collectors.toSet());

        return new UploadTaskResponse(
                task.getId(),
                task.getVideoId(),
                task.getStatus(),
                task.getChunkTotal(),
                task.getUploadedChunks(),
                indexes
        );
    }
}
