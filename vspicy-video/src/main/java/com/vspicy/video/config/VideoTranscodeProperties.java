package com.vspicy.video.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vspicy.video.transcode")
public class VideoTranscodeProperties {
    private Boolean enabled = true;

    /**
     * 历史兼容：是否启用 RocketMQ 转码派发。
     */
    private Boolean rocketMq = true;

    /**
     * 历史兼容：有些配置/代码可能写成 rocketmq。
     */
    private Boolean rocketmq = true;

    private String topic = "vspicy-video-transcode-topic";
    private String transcodeTopic = "vspicy-video-transcode-topic";
    private String retryTopic = "vspicy-video-transcode-retry-topic";
    private String tag = "TRANSCODE";

    private String consumerGroup = "vspicy-video-transcode-consumer";
    private String producerGroup = "vspicy-video-producer";

    private String ffmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";

    private Integer segmentSeconds = 6;
    private Integer hlsSegmentSeconds = 6;
    private String hlsPlaylistName = "index.m3u8";

    private Boolean overwrite = true;
    private Boolean deleteOriginAfterTranscode = false;
    private Integer maxRetryCount = 3;

    private String inputFormat = "mp4";
    private String outputFormat = "hls";
    private String videoCodec = "libx264";
    private String audioCodec = "aac";
    private String preset = "veryfast";
    private String crf = "23";
    private String resolution = "720p";
    private Integer timeoutSeconds = 3600;

    public Boolean getEnabled() {
        return enabled;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getRocketMq() {
        return rocketMq;
    }

    public boolean isRocketMq() {
        return Boolean.TRUE.equals(rocketMq);
    }

    public void setRocketMq(Boolean rocketMq) {
        this.rocketMq = rocketMq;
        this.rocketmq = rocketMq;
    }

    public Boolean getRocketmq() {
        return rocketmq;
    }

    public boolean isRocketmq() {
        return Boolean.TRUE.equals(rocketmq);
    }

    public void setRocketmq(Boolean rocketmq) {
        this.rocketmq = rocketmq;
        this.rocketMq = rocketmq;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
        this.transcodeTopic = topic;
    }

    public String getTranscodeTopic() {
        return transcodeTopic;
    }

    public void setTranscodeTopic(String transcodeTopic) {
        this.transcodeTopic = transcodeTopic;
        this.topic = transcodeTopic;
    }

    public String getRetryTopic() {
        return retryTopic;
    }

    public void setRetryTopic(String retryTopic) {
        this.retryTopic = retryTopic;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public String getFfprobePath() {
        return ffprobePath;
    }

    public void setFfprobePath(String ffprobePath) {
        this.ffprobePath = ffprobePath;
    }

    public Integer getSegmentSeconds() {
        return segmentSeconds;
    }

    public void setSegmentSeconds(Integer segmentSeconds) {
        this.segmentSeconds = segmentSeconds;
        this.hlsSegmentSeconds = segmentSeconds;
    }

    public Integer getHlsSegmentSeconds() {
        return hlsSegmentSeconds;
    }

    public void setHlsSegmentSeconds(Integer hlsSegmentSeconds) {
        this.hlsSegmentSeconds = hlsSegmentSeconds;
        this.segmentSeconds = hlsSegmentSeconds;
    }

    public String getHlsPlaylistName() {
        return hlsPlaylistName;
    }

    public void setHlsPlaylistName(String hlsPlaylistName) {
        this.hlsPlaylistName = hlsPlaylistName;
    }

    public Boolean getOverwrite() {
        return overwrite;
    }

    public boolean isOverwrite() {
        return Boolean.TRUE.equals(overwrite);
    }

    public void setOverwrite(Boolean overwrite) {
        this.overwrite = overwrite;
    }

    public Boolean getDeleteOriginAfterTranscode() {
        return deleteOriginAfterTranscode;
    }

    public boolean isDeleteOriginAfterTranscode() {
        return Boolean.TRUE.equals(deleteOriginAfterTranscode);
    }

    public void setDeleteOriginAfterTranscode(Boolean deleteOriginAfterTranscode) {
        this.deleteOriginAfterTranscode = deleteOriginAfterTranscode;
    }

    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public String getInputFormat() {
        return inputFormat;
    }

    public void setInputFormat(String inputFormat) {
        this.inputFormat = inputFormat;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public String getVideoCodec() {
        return videoCodec;
    }

    public void setVideoCodec(String videoCodec) {
        this.videoCodec = videoCodec;
    }

    public String getAudioCodec() {
        return audioCodec;
    }

    public void setAudioCodec(String audioCodec) {
        this.audioCodec = audioCodec;
    }

    public String getPreset() {
        return preset;
    }

    public void setPreset(String preset) {
        this.preset = preset;
    }

    public String getCrf() {
        return crf;
    }

    public void setCrf(String crf) {
        this.crf = crf;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
