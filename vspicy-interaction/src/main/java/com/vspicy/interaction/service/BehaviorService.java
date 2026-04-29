package com.vspicy.interaction.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.interaction.dto.BehaviorLogCommand;
import com.vspicy.interaction.entity.UserBehaviorLog;
import com.vspicy.interaction.mapper.UserBehaviorLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class BehaviorService {
    private final UserBehaviorLogMapper mapper;

    public BehaviorService(UserBehaviorLogMapper mapper) {
        this.mapper = mapper;
    }

    public UserBehaviorLog record(BehaviorLogCommand command, HttpServletRequest request) {
        if (command == null || command.targetId() == null || command.targetType() == null || command.actionType() == null) {
            throw new BizException("行为参数不完整");
        }

        UserBehaviorLog log = new UserBehaviorLog();
        log.setUserId(command.userId() == null ? 1L : command.userId());
        log.setTargetId(command.targetId());
        log.setTargetType(command.targetType());
        log.setActionType(command.actionType());
        log.setDurationSeconds(command.durationSeconds() == null ? 0 : command.durationSeconds());
        log.setExtraJson(command.extraJson());
        log.setClientIp(resolveIp(request));
        log.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        mapper.insert(log);
        return log;
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
