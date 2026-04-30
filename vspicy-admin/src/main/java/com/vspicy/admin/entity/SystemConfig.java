package com.vspicy.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_config")
public class SystemConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configKey;
    private String configName;
    private String configValue;
    private String configType;
    private String groupCode;
    private String description;
    private Integer editable;
    private Integer encrypted;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getConfigKey() { return configKey; }
    public String getConfigName() { return configName; }
    public String getConfigValue() { return configValue; }
    public String getConfigType() { return configType; }
    public String getGroupCode() { return groupCode; }
    public String getDescription() { return description; }
    public Integer getEditable() { return editable; }
    public Integer getEncrypted() { return encrypted; }
    public Integer getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public void setConfigName(String configName) { this.configName = configName; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public void setConfigType(String configType) { this.configType = configType; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
    public void setDescription(String description) { this.description = description; }
    public void setEditable(Integer editable) { this.editable = editable; }
    public void setEncrypted(Integer encrypted) { this.encrypted = encrypted; }
    public void setStatus(Integer status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
