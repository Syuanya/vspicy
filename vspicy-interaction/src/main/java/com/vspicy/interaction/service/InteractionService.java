package com.vspicy.interaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.interaction.dto.BehaviorLogCommand;
import com.vspicy.interaction.dto.InteractionStatusResponse;
import com.vspicy.interaction.dto.InteractionToggleCommand;
import com.vspicy.interaction.entity.FavoriteRecord;
import com.vspicy.interaction.entity.LikeRecord;
import com.vspicy.interaction.mapper.FavoriteRecordMapper;
import com.vspicy.interaction.mapper.LikeRecordMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InteractionService {
    private final LikeRecordMapper likeMapper;
    private final FavoriteRecordMapper favoriteMapper;
    private final CommentService commentService;
    private final BehaviorService behaviorService;

    public InteractionService(
            LikeRecordMapper likeMapper,
            FavoriteRecordMapper favoriteMapper,
            CommentService commentService,
            BehaviorService behaviorService
    ) {
        this.likeMapper = likeMapper;
        this.favoriteMapper = favoriteMapper;
        this.commentService = commentService;
        this.behaviorService = behaviorService;
    }

    @Transactional
    public InteractionStatusResponse toggleLike(InteractionToggleCommand command, HttpServletRequest request) {
        validate(command);
        Long userId = command.userId() == null ? 1L : command.userId();

        LikeRecord record = likeMapper.selectOne(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getTargetId, command.targetId())
                .eq(LikeRecord::getTargetType, command.targetType())
                .last("LIMIT 1"));

        if (record == null) {
            record = new LikeRecord();
            record.setUserId(userId);
            record.setTargetId(command.targetId());
            record.setTargetType(command.targetType());
            record.setStatus(1);
            likeMapper.insert(record);
        } else {
            record.setStatus(record.getStatus() == 1 ? 0 : 1);
            likeMapper.updateById(record);
        }

        behaviorService.record(new BehaviorLogCommand(
                userId, command.targetId(), command.targetType(),
                record.getStatus() == 1 ? "LIKE" : "UNLIKE",
                0, null
        ), request);

        return status(userId, command.targetId(), command.targetType());
    }

    @Transactional
    public InteractionStatusResponse toggleFavorite(InteractionToggleCommand command, HttpServletRequest request) {
        validate(command);
        Long userId = command.userId() == null ? 1L : command.userId();

        FavoriteRecord record = favoriteMapper.selectOne(new LambdaQueryWrapper<FavoriteRecord>()
                .eq(FavoriteRecord::getUserId, userId)
                .eq(FavoriteRecord::getTargetId, command.targetId())
                .eq(FavoriteRecord::getTargetType, command.targetType())
                .last("LIMIT 1"));

        if (record == null) {
            record = new FavoriteRecord();
            record.setUserId(userId);
            record.setTargetId(command.targetId());
            record.setTargetType(command.targetType());
            record.setStatus(1);
            favoriteMapper.insert(record);
        } else {
            record.setStatus(record.getStatus() == 1 ? 0 : 1);
            favoriteMapper.updateById(record);
        }

        behaviorService.record(new BehaviorLogCommand(
                userId, command.targetId(), command.targetType(),
                record.getStatus() == 1 ? "FAVORITE" : "UNFAVORITE",
                0, null
        ), request);

        return status(userId, command.targetId(), command.targetType());
    }

    public InteractionStatusResponse status(Long userId, Long targetId, String targetType) {
        if (userId == null) {
            userId = 1L;
        }
        if (targetId == null || targetType == null || targetType.isBlank()) {
            throw new BizException("查询参数不完整");
        }

        long likeCount = likeMapper.selectCount(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getTargetId, targetId)
                .eq(LikeRecord::getTargetType, targetType)
                .eq(LikeRecord::getStatus, 1));

        long favoriteCount = favoriteMapper.selectCount(new LambdaQueryWrapper<FavoriteRecord>()
                .eq(FavoriteRecord::getTargetId, targetId)
                .eq(FavoriteRecord::getTargetType, targetType)
                .eq(FavoriteRecord::getStatus, 1));

        long commentCount = commentService.count(targetId, targetType);

        LikeRecord like = likeMapper.selectOne(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getTargetId, targetId)
                .eq(LikeRecord::getTargetType, targetType)
                .last("LIMIT 1"));

        FavoriteRecord favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<FavoriteRecord>()
                .eq(FavoriteRecord::getUserId, userId)
                .eq(FavoriteRecord::getTargetId, targetId)
                .eq(FavoriteRecord::getTargetType, targetType)
                .last("LIMIT 1"));

        return new InteractionStatusResponse(
                userId,
                targetId,
                targetType,
                like != null && like.getStatus() == 1,
                favorite != null && favorite.getStatus() == 1,
                likeCount,
                favoriteCount,
                commentCount
        );
    }

    private void validate(InteractionToggleCommand command) {
        if (command == null || command.targetId() == null || command.targetType() == null || command.targetType().isBlank()) {
            throw new BizException("互动参数不完整");
        }
    }
}
