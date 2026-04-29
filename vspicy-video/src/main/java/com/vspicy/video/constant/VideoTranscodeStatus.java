package com.vspicy.video.constant;

import java.util.Set;

public final class VideoTranscodeStatus {
    public static final String PENDING = "PENDING";
    public static final String DISPATCHED = "DISPATCHED";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String CANCELED = "CANCELED";

    private static final Set<String> RETRYABLE = Set.of(PENDING, FAILED, CANCELED);
    private static final Set<String> CANCELABLE = Set.of(PENDING, DISPATCHED, RUNNING, FAILED);

    private VideoTranscodeStatus() {
    }

    public static boolean isRetryable(String status) {
        return RETRYABLE.contains(status);
    }

    public static boolean isCancelable(String status) {
        return CANCELABLE.contains(status);
    }
}
