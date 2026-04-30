package com.vspicy.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vspicy.minio")
public class MinioProperties {
    private String endpoint = "http://127.0.0.1:9000";
    private String accessKey = "change_me";
    private String secretKey = "change_me";
    private String bucket = "vspicy";

    public String getEndpoint() { return endpoint; }
    public String getAccessKey() { return accessKey; }
    public String getSecretKey() { return secretKey; }
    public String getBucket() { return bucket; }

    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public void setBucket(String bucket) { this.bucket = bucket; }
}
