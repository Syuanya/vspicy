package com.vspicy.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("video_upload_task")
public class VideoUploadTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long videoId;
    private String fileName;
    private String fileHash;
    private Long fileSize;
    private Long chunkSize;
    private Integer chunkTotal;
    private Integer uploadedChunks;
    private String status;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getVideoId() { return videoId; }
    public String getFileName() { return fileName; }
    public String getFileHash() { return fileHash; }
    public Long getFileSize() { return fileSize; }
    public Long getChunkSize() { return chunkSize; }
    public Integer getChunkTotal() { return chunkTotal; }
    public Integer getUploadedChunks() { return uploadedChunks; }
    public String getStatus() { return status; }

    public void setId(Long id) { this.id = id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public void setChunkSize(Long chunkSize) { this.chunkSize = chunkSize; }
    public void setChunkTotal(Integer chunkTotal) { this.chunkTotal = chunkTotal; }
    public void setUploadedChunks(Integer uploadedChunks) { this.uploadedChunks = uploadedChunks; }
    public void setStatus(String status) { this.status = status; }
}
