package com.vspicy.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.content.dto.ModerationResult;
import com.vspicy.content.entity.Article;
import com.vspicy.content.entity.ContentModerationRecord;
import com.vspicy.content.entity.SensitiveWord;
import com.vspicy.content.mapper.ContentModerationRecordMapper;
import com.vspicy.content.mapper.SensitiveWordMapper;
import com.vspicy.content.security.HtmlSanitizer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ContentModerationService {
    private final SensitiveWordMapper sensitiveWordMapper;
    private final ContentModerationRecordMapper recordMapper;
    private final HtmlSanitizer htmlSanitizer;

    public ContentModerationService(
            SensitiveWordMapper sensitiveWordMapper,
            ContentModerationRecordMapper recordMapper,
            HtmlSanitizer htmlSanitizer
    ) {
        this.sensitiveWordMapper = sensitiveWordMapper;
        this.recordMapper = recordMapper;
        this.htmlSanitizer = htmlSanitizer;
    }

    public ModerationResult checkArticle(Article article, String htmlContent) {
        String text = normalize(article.getTitle() + " " + nullToBlank(article.getSummary()) + " " + htmlSanitizer.plainText(htmlContent));
        List<SensitiveWord> activeWords = sensitiveWordMapper.selectList(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getStatus, 1));

        List<SensitiveWord> matched = new ArrayList<>();
        for (SensitiveWord word : activeWords) {
            if (word.getWord() != null && !word.getWord().isBlank() && text.contains(normalize(word.getWord()))) {
                matched.add(word);
            }
        }

        matched = matched.stream()
                .sorted(Comparator.comparing(SensitiveWord::getRiskLevel).reversed())
                .toList();

        String riskLevel = resolveRiskLevel(matched);
        String result = matched.isEmpty() ? "PASS" : "REVIEW";
        String matchedWords = matched.stream()
                .map(SensitiveWord::getWord)
                .distinct()
                .collect(Collectors.joining(","));
        String reason = matched.isEmpty() ? "规则检测通过" : "命中敏感词：" + matchedWords;

        ContentModerationRecord record = new ContentModerationRecord();
        record.setBizId(article.getId());
        record.setBizType("ARTICLE");
        record.setUserId(article.getUserId());
        record.setCheckType("RULE");
        record.setResult(result);
        record.setRiskLevel(riskLevel);
        record.setMatchedWords(matchedWords);
        record.setReason(reason);
        recordMapper.insert(record);

        return new ModerationResult(result, riskLevel, matched.stream().map(SensitiveWord::getWord).distinct().toList(), reason);
    }

    public List<ContentModerationRecord> listRecords(String bizType, String result, Long userId, Integer limit) {
        LambdaQueryWrapper<ContentModerationRecord> wrapper = new LambdaQueryWrapper<ContentModerationRecord>()
                .orderByDesc(ContentModerationRecord::getId);

        if (bizType != null && !bizType.isBlank()) {
            wrapper.eq(ContentModerationRecord::getBizType, bizType);
        }
        if (result != null && !result.isBlank()) {
            wrapper.eq(ContentModerationRecord::getResult, result);
        }
        if (userId != null) {
            wrapper.eq(ContentModerationRecord::getUserId, userId);
        }

        int safeLimit = limit == null || limit <= 0 || limit > 200 ? 100 : limit;
        wrapper.last("LIMIT " + safeLimit);
        return recordMapper.selectList(wrapper);
    }

    private String resolveRiskLevel(List<SensitiveWord> matched) {
        if (matched.isEmpty()) {
            return "LOW";
        }
        boolean high = matched.stream().anyMatch(word -> "HIGH".equalsIgnoreCase(word.getRiskLevel()));
        if (high) {
            return "HIGH";
        }
        boolean medium = matched.stream().anyMatch(word -> "MEDIUM".equalsIgnoreCase(word.getRiskLevel()));
        return medium ? "MEDIUM" : "LOW";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("\\s+", "");
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
