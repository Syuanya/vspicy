package com.vspicy.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("file_object")
public class FileObject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private String originalName;
    private String contentType;
    private String storageType;
    private String bucket;
    private String objectKey;
    private String url;
    private Long sizeBytes;
    private String checksum;
    private Long uploaderId;

    public Long getId() { return id; }
    public String getBizType() { return bizType; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public String getStorageType() { return storageType; }
    public String getBucket() { return bucket; }
    public String getObjectKey() { return objectKey; }
    public String getUrl() { return url; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getChecksum() { return checksum; }
    public Long getUploaderId() { return uploaderId; }

    public void setId(Long id) { this.id = id; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public void setUrl(String url) { this.url = url; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public void setUploaderId(Long uploaderId) { this.uploaderId = uploaderId; }
}
