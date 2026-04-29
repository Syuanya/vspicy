package com.vspicy.video.dto;

import java.util.Set;

public record UploadTaskResponse(
        Long taskId,
        Long videoId,
        String status,
        Integer chunkTotal,
        Integer uploadedChunks,
        Set<Integer> uploadedChunkIndexes
) {
}
