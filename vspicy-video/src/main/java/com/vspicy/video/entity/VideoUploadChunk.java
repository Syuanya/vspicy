package com.vspicy.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("video_upload_chunk")
public class VideoUploadChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer chunkIndex;
    private String chunkHash;
    private Long sizeBytes;
    private String storagePath;
    private String status;

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public String getChunkHash() { return chunkHash; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getStoragePath() { return storagePath; }
    public String getStatus() { return status; }

    public void setId(Long id) { this.id = id; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public void setChunkHash(String chunkHash) { this.chunkHash = chunkHash; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public void setStatus(String status) { this.status = status; }
}
