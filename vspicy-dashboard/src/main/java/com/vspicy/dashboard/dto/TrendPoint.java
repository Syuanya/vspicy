package com.vspicy.dashboard.dto;

public record TrendPoint(
        String date,
        long viewCount,
        long playCount,
        long likeCount,
        long favoriteCount,
        long commentCount,
        long exposureCount
) {
}
