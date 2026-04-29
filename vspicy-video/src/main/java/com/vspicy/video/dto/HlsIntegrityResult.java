package com.vspicy.video.dto;

import java.util.List;

public record HlsIntegrityResult(
        String bucket,
        String prefix,
        Integer limit,
        Long manifestCount,
        Long okCount,
        Long manifestMissingCount,
        Long manifestEmptyCount,
        Long manifestReadFailedCount,
        Long segmentMissingCount,
        List<HlsIntegrityItem> items
) {
}
