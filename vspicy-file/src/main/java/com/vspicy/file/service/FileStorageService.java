package com.vspicy.file.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.file.config.MinioProperties;
import com.vspicy.file.dto.FileUploadResponse;
import com.vspicy.file.entity.FileObject;
import com.vspicy.file.mapper.FileObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class FileStorageService {
    private final MinioClient minioClient;
    private final MinioProperties properties;
    private final FileObjectMapper fileObjectMapper;

    public FileStorageService(MinioClient minioClient, MinioProperties properties, FileObjectMapper fileObjectMapper) {
        this.minioClient = minioClient;
        this.properties = properties;
        this.fileObjectMapper = fileObjectMapper;
    }

    @PostConstruct
    public void initBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(properties.getBucket())
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(properties.getBucket())
                    .build());
        }
    }

    public FileUploadResponse upload(MultipartFile file, String bizType, Long uploaderId) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        try {
            String originalName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
            String suffix = "";
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0) {
                suffix = originalName.substring(dot);
            }

            String checksum;
            try (InputStream checksumStream = file.getInputStream()) {
                checksum = DigestUtils.sha256Hex(checksumStream);
            }

            String objectKey = "%s/%s/%s%s".formatted(
                    bizType == null || bizType.isBlank() ? "COMMON" : bizType,
                    LocalDate.now(),
                    UUID.randomUUID(),
                    suffix
            );

            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(objectKey)
                        .contentType(file.getContentType())
                        .stream(inputStream, file.getSize(), -1)
                        .build());
            }

            String url = properties.getEndpoint() + "/" + properties.getBucket() + "/" + objectKey;

            FileObject record = new FileObject();
            record.setBizType(bizType == null || bizType.isBlank() ? "COMMON" : bizType);
            record.setOriginalName(originalName);
            record.setContentType(file.getContentType());
            record.setStorageType("MINIO");
            record.setBucket(properties.getBucket());
            record.setObjectKey(objectKey);
            record.setUrl(url);
            record.setSizeBytes(file.getSize());
            record.setChecksum(checksum);
            record.setUploaderId(uploaderId);
            fileObjectMapper.insert(record);

            return new FileUploadResponse(record.getId(), originalName, properties.getBucket(), objectKey, url, file.getSize(), checksum);
        } catch (Exception ex) {
            throw new BizException(500, "文件上传失败: " + ex.getMessage());
        }
    }
}
