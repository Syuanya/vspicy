package com.vspicy.video.mq;

import java.io.Serializable;

public class VideoTranscodeMessage implements Serializable {
    private Long transcodeTaskId;
    private Long videoId;

    public VideoTranscodeMessage() {
    }

    public VideoTranscodeMessage(Long transcodeTaskId, Long videoId) {
        this.transcodeTaskId = transcodeTaskId;
        this.videoId = videoId;
    }

    public Long getTranscodeTaskId() {
        return transcodeTaskId;
    }

    public Long getVideoId() {
        return videoId;
    }

    public void setTranscodeTaskId(Long transcodeTaskId) {
        this.transcodeTaskId = transcodeTaskId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }
}
