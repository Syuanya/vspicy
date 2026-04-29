package com.vspicy.recommend.dto;

import java.util.List;
import java.util.Map;

public record RecommendationDebugResponse(
        Long userId,
        List<Map<String, Object>> interests,
        List<Map<String, Object>> recentBehaviors,
        List<Map<String, Object>> recentExposures,
        List<RecommendationItem> feed
) {
}
