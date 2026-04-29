package com.vspicy.video.dto;

import java.util.List;

public record VideoFileConsistencyResult(
        String bucket,
        String prefix,
        Integer limit,
        Boolean videoFileTableExists,
        String videoFileObjectColumn,
        Long videoFileObjectCount,
        Long traceObjectCount,
        Long minioObjectCount,
        Long videoFileMissingObjectCount,
        Long videoFileMissingTraceCount,
        Long traceMissingVideoFileCount,
        Long objectMissingVideoFileCount,
        List<VideoFileConsistencyItem> items
) {
}
