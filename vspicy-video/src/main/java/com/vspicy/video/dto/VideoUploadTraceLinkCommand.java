package com.vspicy.video.dto;

public record VideoUploadTraceLinkCommand(
        Long userId,
        Long recordId,
        Long videoId,
        String uploadTaskId,
        String bucket,
        String objectKey,
        String fileName,
        Long sizeMb,
        String source,
        String remark
) {
}
