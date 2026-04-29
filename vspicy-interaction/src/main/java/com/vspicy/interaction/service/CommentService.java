package com.vspicy.interaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.interaction.dto.BehaviorLogCommand;
import com.vspicy.interaction.dto.CommentCreateCommand;
import com.vspicy.interaction.entity.Comment;
import com.vspicy.interaction.mapper.CommentMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {
    private final CommentMapper commentMapper;
    private final BehaviorService behaviorService;

    public CommentService(CommentMapper commentMapper, BehaviorService behaviorService) {
        this.commentMapper = commentMapper;
        this.behaviorService = behaviorService;
    }

    @Transactional
    public Comment create(CommentCreateCommand command, HttpServletRequest request) {
        if (command == null || command.contentId() == null || command.contentType() == null) {
            throw new BizException("评论目标不能为空");
        }
        if (command.content() == null || command.content().isBlank()) {
            throw new BizException("评论内容不能为空");
        }
        if (command.content().length() > 2000) {
            throw new BizException("评论内容不能超过2000字符");
        }

        Comment comment = new Comment();
        comment.setContentId(command.contentId());
        comment.setContentType(command.contentType());
        comment.setParentId(command.parentId() == null ? 0L : command.parentId());
        comment.setUserId(command.userId() == null ? 1L : command.userId());
        comment.setReplyToUserId(command.replyToUserId());
        comment.setContent(command.content());
        comment.setStatus("NORMAL");
        comment.setLikeCount(0L);
        comment.setDeleted(0);
        commentMapper.insert(comment);

        behaviorService.record(new BehaviorLogCommand(
                comment.getUserId(),
                command.contentId(),
                command.contentType(),
                "COMMENT",
                0,
                "{\"commentId\":" + comment.getId() + "}"
        ), request);

        return comment;
    }

    public List<Comment> list(Long contentId, String contentType, Long parentId, Integer limit) {
        if (contentId == null || contentType == null || contentType.isBlank()) {
            throw new BizException("查询参数不完整");
        }

        int safeLimit = limit == null || limit <= 0 || limit > 200 ? 50 : limit;

        return commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getContentId, contentId)
                .eq(Comment::getContentType, contentType)
                .eq(Comment::getDeleted, 0)
                .eq(parentId != null, Comment::getParentId, parentId)
                .orderByDesc(Comment::getId)
                .last("LIMIT " + safeLimit));
    }

    public long count(Long contentId, String contentType) {
        return commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getContentId, contentId)
                .eq(Comment::getContentType, contentType)
                .eq(Comment::getDeleted, 0));
    }
}
