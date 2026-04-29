package com.vspicy.file.dto;

public record FileUploadResponse(
        Long id,
        String originalName,
        String bucket,
        String objectKey,
        String url,
        Long sizeBytes,
        String checksum
) {
}
