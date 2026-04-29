package com.vspicy.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.content.dto.SensitiveWordCommand;
import com.vspicy.content.entity.SensitiveWord;
import com.vspicy.content.mapper.SensitiveWordMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SensitiveWordService {
    private final SensitiveWordMapper mapper;

    public SensitiveWordService(SensitiveWordMapper mapper) {
        this.mapper = mapper;
    }

    public List<SensitiveWord> list(String category, Integer status, Integer limit) {
        LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<SensitiveWord>()
                .orderByDesc(SensitiveWord::getId);

        if (category != null && !category.isBlank()) {
            wrapper.eq(SensitiveWord::getCategory, category);
        }
        if (status != null) {
            wrapper.eq(SensitiveWord::getStatus, status);
        }

        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 200 : limit;
        wrapper.last("LIMIT " + safeLimit);
        return mapper.selectList(wrapper);
    }

    public SensitiveWord create(SensitiveWordCommand command) {
        if (command == null || command.word() == null || command.word().isBlank()) {
            throw new BizException("敏感词不能为空");
        }

        SensitiveWord existed = mapper.selectOne(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getWord, command.word())
                .last("LIMIT 1"));

        if (existed != null) {
            return existed;
        }

        SensitiveWord word = new SensitiveWord();
        word.setWord(command.word().trim());
        word.setCategory(command.category() == null || command.category().isBlank() ? "GENERAL" : command.category());
        word.setRiskLevel(command.riskLevel() == null || command.riskLevel().isBlank() ? "MEDIUM" : command.riskLevel());
        word.setStatus(1);
        mapper.insert(word);
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

    private SensitiveWord require(Long id) {
        SensitiveWord word = mapper.selectById(id);
        if (word == null) {
            throw new BizException(404, "敏感词不存在");
        }
        return word;
    }
}
