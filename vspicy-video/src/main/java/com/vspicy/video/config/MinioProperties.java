package com.vspicy.video.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vspicy.video.minio")
public class MinioProperties {
    private String endpoint = "http://127.0.0.1:9000";
    private String accessKey = "change_me";
    private String secretKey = "change_me";
    private String bucket = "vspicy";
    private String publicEndpoint = "http://127.0.0.1:9000";
    private String publicBaseUrl = "http://127.0.0.1:9000/vspicy";
    private String region = "us-east-1";
    private Boolean secure = false;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    /**
     * 兼容历史代码可能使用 accessId 命名。
     */
    public String getAccessId() {
        return accessKey;
    }

    public void setAccessId(String accessId) {
        this.accessKey = accessId;
    }

    /**
     * 兼容历史代码可能使用 accessSecret 命名。
     */
    public String getAccessSecret() {
        return secretKey;
    }

    public void setAccessSecret(String accessSecret) {
        this.secretKey = accessSecret;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    /**
     * 兼容历史代码可能使用 bucketName 命名。
     */
    public String getBucketName() {
        return bucket;
    }

    public void setBucketName(String bucketName) {
        this.bucket = bucketName;
    }

    public String getPublicEndpoint() {
        return publicEndpoint;
    }

    public void setPublicEndpoint(String publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
        this.publicBaseUrl = publicEndpoint + "/" + bucket;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * 兼容部分旧代码：baseUrl 与 publicBaseUrl 同义。
     */
    public String getBaseUrl() {
        return publicBaseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.publicBaseUrl = baseUrl;
    }

    /**
     * 兼容部分旧代码：url 与 publicBaseUrl 同义。
     */
    public String getUrl() {
        return publicBaseUrl;
    }

    public void setUrl(String url) {
        this.publicBaseUrl = url;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Boolean getSecure() {
        return secure;
    }

    public boolean isSecure() {
        return Boolean.TRUE.equals(secure);
    }

    public void setSecure(Boolean secure) {
        this.secure = secure;
    }
}
