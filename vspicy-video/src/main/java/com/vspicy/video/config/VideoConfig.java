package com.vspicy.video.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties({
        VideoStorageProperties.class,
        VideoTranscodeProperties.class,
        VideoTranscodeCompensationProperties.class,
        MinioProperties.class
})
public class VideoConfig {

    @Bean
    public MinioClient minioClient(
            @Value("${vspicy.video.minio.endpoint:${vspicy.minio.endpoint:${minio.endpoint:http://127.0.0.1:9000}}}") String endpoint,
            @Value("${vspicy.video.minio.access-key:${vspicy.minio.access-key:${minio.access-key:change_me}}}") String accessKey,
            @Value("${vspicy.video.minio.secret-key:${vspicy.minio.secret-key:${minio.secret-key:change_me}}}") String secretKey
    ) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("MinIO endpoint 不能为空，请配置 vspicy.video.minio.endpoint");
        }
        if (accessKey == null || accessKey.isBlank()) {
            throw new IllegalStateException("MinIO access-key 不能为空，请配置 vspicy.video.minio.access-key");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("MinIO secret-key 不能为空，请配置 vspicy.video.minio.secret-key");
        }

        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public Path videoOriginRoot(VideoStorageProperties storageProperties) throws Exception {
        Path path = Path.of(storageProperties.getOriginDir());
        Files.createDirectories(path);
        return path;
    }

    @Bean
    public Path videoHlsRoot(VideoStorageProperties storageProperties) throws Exception {
        Path path = Path.of(storageProperties.getHlsDir());
        Files.createDirectories(path);
        return path;
    }

    @Bean
    public Path videoTmpRoot(VideoStorageProperties storageProperties) throws Exception {
        Path path = Path.of(storageProperties.getTmpDir());
        Files.createDirectories(path);
        return path;
    }

    @Bean
    public Path videoMergedRoot(VideoStorageProperties storageProperties) throws Exception {
        Path path = Path.of(storageProperties.getMergedDir());
        Files.createDirectories(path);
        return path;
    }
}
