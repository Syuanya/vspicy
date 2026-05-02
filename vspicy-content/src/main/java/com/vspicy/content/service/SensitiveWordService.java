package com.vspicy.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.content.dto.SensitiveWordCommand;
import com.vspicy.content.dto.SensitiveWordMetricItem;
import com.vspicy.content.dto.SensitiveWordOverviewView;
import com.vspicy.content.dto.SensitiveWordTestCommand;
import com.vspicy.content.dto.SensitiveWordTestView;
import com.vspicy.content.entity.SensitiveWord;
import com.vspicy.content.mapper.SensitiveWordMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SensitiveWordService {
    private final SensitiveWordMapper mapper;

    public SensitiveWordService(SensitiveWordMapper mapper) {
        this.mapper = mapper;
    }

    public SensitiveWordOverviewView overview() {
        List<SensitiveWord> rows = mapper.selectList(new LambdaQueryWrapper<SensitiveWord>()
                .orderByDesc(SensitiveWord::getId));

        long total = rows.size();
        long enabled = rows.stream().filter(item -> Objects.equals(item.getStatus(), 1)).count();
        long disabled = rows.stream().filter(item -> !Objects.equals(item.getStatus(), 1)).count();
        long high = rows.stream().filter(item -> "HIGH".equalsIgnoreCase(item.getRiskLevel())).count();
        long medium = rows.stream().filter(item -> "MEDIUM".equalsIgnoreCase(item.getRiskLevel())).count();
        long low = rows.stream().filter(item -> "LOW".equalsIgnoreCase(item.getRiskLevel())).count();

        return new SensitiveWordOverviewView(
                total,
                enabled,
                disabled,
                high,
                medium,
                low,
                metric(rows, "category"),
                metric(rows, "riskLevel")
        );
    }

    public List<SensitiveWord> list(String category, String riskLevel, Integer status, String keyword, Integer limit) {
        LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<SensitiveWord>()
                .orderByDesc(SensitiveWord::getId);

        if (notBlank(category)) {
            wrapper.eq(SensitiveWord::getCategory, normalizeCode(category));
        }
        if (notBlank(riskLevel)) {
            wrapper.eq(SensitiveWord::getRiskLevel, normalizeRisk(riskLevel));
        }
        if (status != null) {
            wrapper.eq(SensitiveWord::getStatus, status);
        }
        if (notBlank(keyword)) {
            String value = keyword.trim();
            wrapper.and(item -> item
                    .like(SensitiveWord::getWord, value)
                    .or()
                    .like(SensitiveWord::getCategory, value)
                    .or()
                    .like(SensitiveWord::getRiskLevel, value));
        }

        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 200 : limit;
        wrapper.last("LIMIT " + safeLimit);
        return mapper.selectList(wrapper);
    }

    public SensitiveWord create(SensitiveWordCommand command) {
        validateCommand(command);
        SensitiveWord existed = mapper.selectOne(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getWord, command.word().trim())
                .last("LIMIT 1"));

        if (existed != null) {
            throw new BizException("敏感词已存在");
        }

        SensitiveWord word = new SensitiveWord();
        word.setWord(command.word().trim());
        word.setCategory(normalizeCategory(command.category()));
        word.setRiskLevel(normalizeRisk(command.riskLevel()));
        word.setStatus(1);
        mapper.insert(word);
        return word;
    }

    public SensitiveWord update(Long id, SensitiveWordCommand command) {
        SensitiveWord word = require(id);
        validateCommand(command);

        SensitiveWord existed = mapper.selectOne(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getWord, command.word().trim())
                .last("LIMIT 1"));
        if (existed != null && !Objects.equals(existed.getId(), id)) {
            throw new BizException("敏感词已存在");
        }

        word.setWord(command.word().trim());
        word.setCategory(normalizeCategory(command.category()));
        word.setRiskLevel(normalizeRisk(command.riskLevel()));
        mapper.updateById(word);
        return word;
    }

    public SensitiveWord enable(Long id) {
        SensitiveWord word = require(id);
        word.setStatus(1);
        mapper.updateById(word);
        return word;
    }

    public SensitiveWord disable(Long id) {
        SensitiveWord word = require(id);
        word.setStatus(0);
        mapper.updateById(word);
        return word;
    }

    public void delete(Long id) {
        SensitiveWord word = require(id);
        mapper.deleteById(word.getId());
    }

    public SensitiveWordTestView test(SensitiveWordTestCommand command) {
        String text = normalizeText(command == null ? null : command.text());
        if (text.isBlank()) {
            throw new BizException("请输入需要检测的文本");
        }

        List<SensitiveWord> activeWords = mapper.selectList(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getStatus, 1));
        List<SensitiveWord> matched = new ArrayList<>();
        for (SensitiveWord word : activeWords) {
            String candidate = normalizeText(word.getWord());
            if (!candidate.isBlank() && text.contains(candidate)) {
                matched.add(word);
            }
        }

        matched = matched.stream()
                .sorted(Comparator.comparingInt(this::riskWeight).reversed())
                .toList();

        String riskLevel = resolveRiskLevel(matched);
        List<String> words = matched.stream()
                .map(SensitiveWord::getWord)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        String result = words.isEmpty() ? "PASS" : "REVIEW";
        String reason = words.isEmpty() ? "规则检测通过" : "命中敏感词：" + String.join(",", words);
        return new SensitiveWordTestView(result, riskLevel, words, reason);
    }

    private SensitiveWord require(Long id) {
        if (id == null) {
            throw new BizException("敏感词ID不能为空");
        }
        SensitiveWord word = mapper.selectById(id);
        if (word == null) {
            throw new BizException(404, "敏感词不存在");
        }
        return word;
    }

    private void validateCommand(SensitiveWordCommand command) {
        if (command == null || !notBlank(command.word())) {
            throw new BizException("敏感词不能为空");
        }
        if (command.word().trim().length() > 120) {
            throw new BizException("敏感词长度不能超过120个字符");
        }
        normalizeRisk(command.riskLevel());
    }

    private List<SensitiveWordMetricItem> metric(List<SensitiveWord> rows, String field) {
        Map<String, Long> grouped = rows.stream().collect(Collectors.groupingBy(item -> {
            String value = "riskLevel".equals(field) ? item.getRiskLevel() : item.getCategory();
            return notBlank(value) ? normalizeCode(value) : "UNKNOWN";
        }, LinkedHashMap::new, Collectors.counting()));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new SensitiveWordMetricItem(entry.getKey(), entry.getValue()))
                .toList();
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

    private int riskWeight(SensitiveWord word) {
        String risk = word == null ? null : word.getRiskLevel();
        if ("HIGH".equalsIgnoreCase(risk)) {
            return 3;
        }
        if ("MEDIUM".equalsIgnoreCase(risk)) {
            return 2;
        }
        return 1;
    }

    private String normalizeCategory(String value) {
        return notBlank(value) ? normalizeCode(value) : "GENERAL";
    }

    private String normalizeRisk(String value) {
        String risk = notBlank(value) ? normalizeCode(value) : "MEDIUM";
        if (!List.of("LOW", "MEDIUM", "HIGH").contains(risk)) {
            throw new BizException("风险等级只能是 LOW、MEDIUM、HIGH");
        }
        return risk;
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
