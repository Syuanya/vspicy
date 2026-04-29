package com.vspicy.video.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vspicy.video.storage")
public class VideoStorageProperties {
    private String originDir = "D:/workspace/vspicy/media/origin";
    private String hlsDir = "D:/workspace/vspicy/media/hls";
    private String tmpDir = "D:/workspace/vspicy/tmp";
    private String mergedDir = "D:/workspace/vspicy/media/merged";

    private String publicBaseUrl = "http://127.0.0.1:9000/vspicy";
    private String hlsUrlPrefix = "http://127.0.0.1:9000/vspicy/videos";

    private String bucket = "vspicy";
    private String publicEndpoint = "http://127.0.0.1:9000";

    private String ffmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";
    private Integer segmentSeconds = 6;
    private Integer hlsSegmentSeconds = 6;
    private String hlsPlaylistName = "index.m3u8";

    public String getOriginDir() {
        return originDir;
    }

    public void setOriginDir(String originDir) {
        this.originDir = originDir;
    }

    public String getHlsDir() {
        return hlsDir;
    }

    public void setHlsDir(String hlsDir) {
        this.hlsDir = hlsDir;
    }

    public String getTmpDir() {
        return tmpDir;
    }

    public void setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
    }

    public String getMergedDir() {
        return mergedDir;
    }

    public void setMergedDir(String mergedDir) {
        this.mergedDir = mergedDir;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getHlsUrlPrefix() {
        return hlsUrlPrefix;
    }

    public void setHlsUrlPrefix(String hlsUrlPrefix) {
        this.hlsUrlPrefix = hlsUrlPrefix;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPublicEndpoint() {
        return publicEndpoint;
    }

    public void setPublicEndpoint(String publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
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

    /**
     * 兼容历史代码：originRoot 与 originDir 同义。
     */
    public String getOriginRoot() {
        return originDir;
    }

    public void setOriginRoot(String originRoot) {
        this.originDir = originRoot;
    }

    /**
     * 兼容历史代码：hlsRoot 与 hlsDir 同义。
     */
    public String getHlsRoot() {
        return hlsDir;
    }

    public void setHlsRoot(String hlsRoot) {
        this.hlsDir = hlsRoot;
    }

    /**
     * 兼容历史代码：tmpRoot 与 tmpDir 同义。
     */
    public String getTmpRoot() {
        return tmpDir;
    }

    public void setTmpRoot(String tmpRoot) {
        this.tmpDir = tmpRoot;
    }

    /**
     * 兼容历史代码：tempRoot 与 tmpDir 同义。
     */
    public String getTempRoot() {
        return tmpDir;
    }

    public void setTempRoot(String tempRoot) {
        this.tmpDir = tempRoot;
    }

    /**
     * 兼容历史代码：mergedRoot 与 mergedDir 同义。
     */
    public String getMergedRoot() {
        return mergedDir;
    }

    public void setMergedRoot(String mergedRoot) {
        this.mergedDir = mergedRoot;
    }

    /**
     * 兼容历史代码：mergeRoot 与 mergedDir 同义。
     */
    public String getMergeRoot() {
        return mergedDir;
    }

    public void setMergeRoot(String mergeRoot) {
        this.mergedDir = mergeRoot;
    }
}
