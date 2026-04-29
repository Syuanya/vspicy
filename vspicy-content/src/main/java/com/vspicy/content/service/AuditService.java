package com.vspicy.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.content.dto.AuditReviewCommand;
import com.vspicy.content.entity.Article;
import com.vspicy.content.entity.AuditTask;
import com.vspicy.content.mapper.ArticleMapper;
import com.vspicy.content.mapper.AuditTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {
    private final AuditTaskMapper auditTaskMapper;
    private final ArticleMapper articleMapper;

    public AuditService(AuditTaskMapper auditTaskMapper, ArticleMapper articleMapper) {
        this.auditTaskMapper = auditTaskMapper;
        this.articleMapper = articleMapper;
    }

    public List<AuditTask> list(String status, String bizType, Integer limit) {
        LambdaQueryWrapper<AuditTask> wrapper = new LambdaQueryWrapper<AuditTask>()
                .orderByDesc(AuditTask::getId);

        if (status != null && !status.isBlank()) {
            wrapper.eq(AuditTask::getStatus, status);
        }
        if (bizType != null && !bizType.isBlank()) {
            wrapper.eq(AuditTask::getBizType, bizType);
        }

        int safeLimit = limit == null || limit <= 0 || limit > 200 ? 100 : limit;
        wrapper.last("LIMIT " + safeLimit);
        return auditTaskMapper.selectList(wrapper);
    }

    @Transactional
    public AuditTask pass(Long taskId, AuditReviewCommand command) {
        AuditTask task = requireTask(taskId);
        if (!"PENDING".equals(task.getStatus())) {
            return task;
        }

        task.setStatus("PASS");
        task.setReviewerId(command == null || command.reviewerId() == null ? 1L : command.reviewerId());
        task.setReason(command == null ? null : command.reason());
        task.setReviewedAt(LocalDateTime.now());
        auditTaskMapper.updateById(task);

        if ("ARTICLE".equals(task.getBizType())) {
            Article article = articleMapper.selectById(task.getBizId());
            if (article != null) {
                article.setStatus("PUBLISHED");
                article.setPublishedAt(LocalDateTime.now());
                articleMapper.updateById(article);
            }
        }

        return task;
    }

    @Transactional
    public AuditTask reject(Long taskId, AuditReviewCommand command) {
        AuditTask task = requireTask(taskId);
        if (!"PENDING".equals(task.getStatus())) {
            return task;
        }

        task.setStatus("REJECT");
        task.setReviewerId(command == null || command.reviewerId() == null ? 1L : command.reviewerId());
        task.setReason(command == null || command.reason() == null || command.reason().isBlank() ? "内容不符合发布规范" : command.reason());
        task.setReviewedAt(LocalDateTime.now());
        auditTaskMapper.updateById(task);

        if ("ARTICLE".equals(task.getBizType())) {
            Article article = articleMapper.selectById(task.getBizId());
            if (article != null) {
                article.setStatus("REJECTED");
                articleMapper.updateById(article);
            }
        }

        return task;
    }

    private AuditTask requireTask(Long taskId) {
        AuditTask task = auditTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "审核任务不存在");
        }
        return task;
    }
}
