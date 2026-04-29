package com.vspicy.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("video_file")
public class VideoFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long videoId;
    private String fileType;
    private String storageType;
    private String bucket;
    private String objectKey;
    private String url;
    private Long sizeBytes;
    private String checksum;

    public Long getId() { return id; }
    public Long getVideoId() { return videoId; }
    public String getFileType() { return fileType; }
    public String getStorageType() { return storageType; }
    public String getBucket() { return bucket; }
    public String getObjectKey() { return objectKey; }
    public String getUrl() { return url; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getChecksum() { return checksum; }

    public void setId(Long id) { this.id = id; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public void setUrl(String url) { this.url = url; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
}
