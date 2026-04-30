package com.vspicy.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.admin.dto.SystemConfigCommand;
import com.vspicy.admin.dto.SystemConfigGroupView;
import com.vspicy.admin.dto.SystemConfigView;
import com.vspicy.admin.entity.SystemConfig;
import com.vspicy.admin.mapper.SystemConfigMapper;
import com.vspicy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class SystemConfigService {
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_.:-]{2,127}$");
    private static final List<String> TYPES = List.of("STRING", "NUMBER", "BOOLEAN", "JSON");

    private final SystemConfigMapper systemConfigMapper;

    public SystemConfigService(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    public List<SystemConfigView> list(String groupCode, String keyword, Integer status, Integer limit) {
        int safeLimit = limit == null || limit <= 0 || limit > 500 ? 100 : limit;
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<SystemConfig>()
                .orderByAsc(SystemConfig::getGroupCode)
                .orderByAsc(SystemConfig::getConfigKey)
                .last("LIMIT " + safeLimit);

        if (groupCode != null && !groupCode.isBlank()) {
            wrapper.eq(SystemConfig::getGroupCode, groupCode.trim());
        }
        if (status != null) {
            wrapper.eq(SystemConfig::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(item -> item
                    .like(SystemConfig::getConfigKey, value)
                    .or()
                    .like(SystemConfig::getConfigName, value)
                    .or()
                    .like(SystemConfig::getDescription, value));
        }

        return systemConfigMapper.selectList(wrapper).stream()
                .map(this::toView)
                .toList();
    }

    public List<SystemConfigGroupView> groups() {
        List<SystemConfig> configs = systemConfigMapper.selectList(new LambdaQueryWrapper<SystemConfig>()
                .orderByAsc(SystemConfig::getGroupCode));
        Map<String, long[]> stats = new LinkedHashMap<>();
        for (SystemConfig config : configs) {
            String groupCode = config.getGroupCode() == null || config.getGroupCode().isBlank() ? "default" : config.getGroupCode();
            long[] values = stats.computeIfAbsent(groupCode, key -> new long[3]);
            values[0]++;
            if (Integer.valueOf(1).equals(config.getStatus())) {
                values[1]++;
            } else {
                values[2]++;
            }
        }
        List<SystemConfigGroupView> result = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : stats.entrySet()) {
            long[] values = entry.getValue();
            result.add(new SystemConfigGroupView(entry.getKey(), values[0], values[1], values[2]));
        }
        return result;
    }

    public SystemConfigView get(Long id) {
        return toView(mustGet(id));
    }

    @Transactional
    public SystemConfigView create(SystemConfigCommand command) {
        validate(command, true);
        String configKey = command.configKey().trim();
        SystemConfig existed = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, configKey)
                .last("LIMIT 1"));
        if (existed != null) {
            throw new BizException("配置键已存在");
        }

        SystemConfig config = new SystemConfig();
        apply(config, command, true);
        systemConfigMapper.insert(config);
        return toView(config);
    }

    @Transactional
    public SystemConfigView update(Long id, SystemConfigCommand command) {
        SystemConfig config = mustGet(id);
        if (!isEditable(config)) {
            throw new BizException("该配置为系统内置配置，不允许编辑");
        }
        validate(command, false);
        if (command.configKey() != null && !command.configKey().isBlank()) {
            String configKey = command.configKey().trim();
            SystemConfig existed = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                    .eq(SystemConfig::getConfigKey, configKey)
                    .ne(SystemConfig::getId, id)
                    .last("LIMIT 1"));
            if (existed != null) {
                throw new BizException("配置键已存在");
            }
        }
        apply(config, command, false);
        systemConfigMapper.updateById(config);
        return toView(mustGet(id));
    }

    @Transactional
    public SystemConfigView enable(Long id) {
        SystemConfig config = mustGet(id);
        config.setStatus(1);
        systemConfigMapper.updateById(config);
        return toView(config);
    }

    @Transactional
    public SystemConfigView disable(Long id) {
        SystemConfig config = mustGet(id);
        config.setStatus(0);
        systemConfigMapper.updateById(config);
        return toView(config);
    }

    @Transactional
    public void delete(Long id) {
        SystemConfig config = mustGet(id);
        if (!isEditable(config)) {
            throw new BizException("该配置为系统内置配置，不允许删除");
        }
        systemConfigMapper.deleteById(id);
    }

    private void validate(SystemConfigCommand command, boolean create) {
        if (command == null) {
            throw new BizException("配置内容不能为空");
        }
        if (create || command.configKey() != null) {
            if (command.configKey() == null || command.configKey().isBlank()) {
                throw new BizException("配置键不能为空");
            }
            String key = command.configKey().trim();
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw new BizException("配置键只能包含小写字母、数字、下划线、点、冒号和短横线，且长度为 3-128");
            }
        }
        if (create || command.configName() != null) {
            if (command.configName() == null || command.configName().isBlank()) {
                throw new BizException("配置名称不能为空");
            }
        }
        if (create || command.configType() != null) {
            String type = normalizeType(command.configType());
            if (!TYPES.contains(type)) {
                throw new BizException("配置类型只能是 STRING/NUMBER/BOOLEAN/JSON");
            }
        }
    }

    private void apply(SystemConfig config, SystemConfigCommand command, boolean create) {
        if (create || command.configKey() != null) {
            config.setConfigKey(command.configKey().trim());
        }
        if (create || command.configName() != null) {
            config.setConfigName(command.configName() == null ? null : command.configName().trim());
        }
        if (create || command.configType() != null) {
            config.setConfigType(normalizeType(command.configType()));
        }
        if (create || command.groupCode() != null) {
            config.setGroupCode(command.groupCode() == null || command.groupCode().isBlank() ? "default" : command.groupCode().trim());
        }
        if (create || command.description() != null) {
            config.setDescription(command.description());
        }
        if (create || command.editable() != null) {
            config.setEditable(Boolean.FALSE.equals(command.editable()) ? 0 : 1);
        }
        if (create || command.encrypted() != null) {
            config.setEncrypted(Boolean.TRUE.equals(command.encrypted()) ? 1 : 0);
        }
        if (command.configValue() != null) {
            config.setConfigValue(command.configValue());
        } else if (create) {
            config.setConfigValue("");
        }
        if (create || command.status() != null) {
            config.setStatus(command.status() == null ? 1 : normalizeStatus(command.status()));
        }
    }

    private SystemConfig mustGet(Long id) {
        if (id == null) {
            throw new BizException("配置 ID 不能为空");
        }
        SystemConfig config = systemConfigMapper.selectById(id);
        if (config == null) {
            throw new BizException(404, "配置不存在");
        }
        return config;
    }

    private String normalizeType(String value) {
        if (value == null || value.isBlank()) {
            return "STRING";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private int normalizeStatus(Integer status) {
        return status != null && status == 0 ? 0 : 1;
    }

    private boolean isEditable(SystemConfig config) {
        return !Integer.valueOf(0).equals(config.getEditable());
    }

    private boolean isEncrypted(SystemConfig config) {
        return Integer.valueOf(1).equals(config.getEncrypted());
    }

    private SystemConfigView toView(SystemConfig config) {
        if (config == null) {
            return null;
        }
        String value = isEncrypted(config) ? "******" : config.getConfigValue();
        return new SystemConfigView(
                config.getId(),
                config.getConfigKey(),
                config.getConfigName(),
                value,
                config.getConfigType(),
                config.getGroupCode(),
                config.getDescription(),
                isEditable(config),
                isEncrypted(config),
                config.getStatus(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
