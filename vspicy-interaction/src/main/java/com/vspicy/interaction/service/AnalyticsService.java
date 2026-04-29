package com.vspicy.interaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.interaction.dto.HotContentResponse;
import com.vspicy.interaction.entity.FavoriteRecord;
import com.vspicy.interaction.entity.LikeRecord;
import com.vspicy.interaction.entity.UserBehaviorLog;
import com.vspicy.interaction.mapper.FavoriteRecordMapper;
import com.vspicy.interaction.mapper.LikeRecordMapper;
import com.vspicy.interaction.mapper.UserBehaviorLogMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AnalyticsService {
    private final UserBehaviorLogMapper behaviorMapper;
    private final LikeRecordMapper likeMapper;
    private final FavoriteRecordMapper favoriteMapper;
    private final CommentService commentService;

    public AnalyticsService(
            UserBehaviorLogMapper behaviorMapper,
            LikeRecordMapper likeMapper,
            FavoriteRecordMapper favoriteMapper,
            CommentService commentService
    ) {
        this.behaviorMapper = behaviorMapper;
        this.likeMapper = likeMapper;
        this.favoriteMapper = favoriteMapper;
        this.commentService = commentService;
    }

    public List<HotContentResponse> hotContent(String targetType, Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 100 ? 20 : limit;
        List<UserBehaviorLog> logs = behaviorMapper.selectList(new LambdaQueryWrapper<UserBehaviorLog>()
                .eq(targetType != null && !targetType.isBlank(), UserBehaviorLog::getTargetType, targetType)
                .orderByDesc(UserBehaviorLog::getId)
                .last("LIMIT 1000"));

        Map<String, MutableScore> map = new HashMap<>();
        for (UserBehaviorLog log : logs) {
            String key = log.getTargetType() + ":" + log.getTargetId();
            MutableScore score = map.computeIfAbsent(key, k -> new MutableScore(log.getTargetId(), log.getTargetType()));
            if ("VIEW".equals(log.getActionType())) score.viewCount++;
            if ("PLAY".equals(log.getActionType())) score.playCount++;
        }

        for (MutableScore score : map.values()) {
            score.likeCount = likeMapper.selectCount(new LambdaQueryWrapper<LikeRecord>()
                    .eq(LikeRecord::getTargetId, score.targetId)
                    .eq(LikeRecord::getTargetType, score.targetType)
                    .eq(LikeRecord::getStatus, 1));
            score.favoriteCount = favoriteMapper.selectCount(new LambdaQueryWrapper<FavoriteRecord>()
                    .eq(FavoriteRecord::getTargetId, score.targetId)
                    .eq(FavoriteRecord::getTargetType, score.targetType)
                    .eq(FavoriteRecord::getStatus, 1));
            score.commentCount = commentService.count(score.targetId, score.targetType);
        }

        return map.values().stream()
                .map(MutableScore::toResponse)
                .sorted(Comparator.comparing(HotContentResponse::hotScore).reversed())
                .limit(safeLimit)
                .toList();
    }

    private static class MutableScore {
        Long targetId;
        String targetType;
        long viewCount;
        long playCount;
        long likeCount;
        long favoriteCount;
        long commentCount;

        MutableScore(Long targetId, String targetType) {
            this.targetId = targetId;
            this.targetType = targetType;
        }

        HotContentResponse toResponse() {
            double score = viewCount * 1.0 + playCount * 2.0 + likeCount * 5.0 + favoriteCount * 8.0 + commentCount * 6.0;
            return new HotContentResponse(targetId, targetType, viewCount, playCount, likeCount, favoriteCount, commentCount, score);
        }
    }
}
