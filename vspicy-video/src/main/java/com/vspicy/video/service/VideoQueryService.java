package com.vspicy.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.video.entity.Video;
import com.vspicy.video.entity.VideoFile;
import com.vspicy.video.mapper.VideoFileMapper;
import com.vspicy.video.mapper.VideoMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class VideoQueryService {
    private static final Set<String> EDITABLE_STATUSES = Set.of(
            "UPLOADING",
            "UPLOADED",
            "MERGING",
            "TRANSCODING",
            "PUBLISHED",
            "FAILED",
            "HIDDEN",
            "OFFLINE"
    );

    private final VideoMapper videoMapper;
    private final VideoFileMapper videoFileMapper;

    public VideoQueryService(VideoMapper videoMapper, VideoFileMapper videoFileMapper) {
        this.videoMapper = videoMapper;
        this.videoFileMapper = videoFileMapper;
    }

    public Video getVideo(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new BizException(404, "视频不存在");
        }
        return video;
    }

    public List<Video> listVideos(String status, Long userId, Integer limit) {
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .orderByDesc(Video::getId)
                .last("LIMIT " + safeLimit);

        if (status != null && !status.isBlank()) {
            wrapper.eq(Video::getStatus, status.trim());
        }
        if (userId != null) {
            wrapper.eq(Video::getUserId, userId);
        }

        return videoMapper.selectList(wrapper);
    }

    public Video updateStatus(Long videoId, String status) {
        if (status == null || !EDITABLE_STATUSES.contains(status.trim())) {
            throw new BizException(400, "视频状态不合法");
        }
        Video video = getVideo(videoId);
        video.setStatus(status.trim());
        videoMapper.updateById(video);
        return video;
    }

    public List<VideoFile> getVideoFiles(Long videoId) {
        return videoFileMapper.selectList(new LambdaQueryWrapper<VideoFile>()
                .eq(VideoFile::getVideoId, videoId));
    }
}
