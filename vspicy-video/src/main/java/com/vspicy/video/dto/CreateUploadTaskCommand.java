package com.vspicy.video.dto;

public record CreateUploadTaskCommand(
        Long userId,
        String title,
        String fileName,
        String fileHash,
        Long fileSize,
        Long chunkSize
) {
}
