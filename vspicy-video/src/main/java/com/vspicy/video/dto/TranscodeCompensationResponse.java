package com.vspicy.video.dto;

import java.util.List;

public record TranscodeCompensationResponse(
        int scanned,
        int submitted,
        int skipped,
        List<Long> submittedTaskIds,
        String message
) {
}
