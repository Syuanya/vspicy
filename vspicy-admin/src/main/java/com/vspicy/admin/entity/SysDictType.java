package com.vspicy.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_dict_type")
public class SysDictType {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String typeCode;
    private String typeName;
    private String description;
    private Integer status;
    private Integer editable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getTypeCode() { return typeCode; }
    public String getTypeName() { return typeName; }
    public String getDescription() { return description; }
    public Integer getStatus() { return status; }
    public Integer getEditable() { return editable; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public void setTypeName(String typeName) { this.typeName = typeName; }
    public void setDescription(String description) { this.description = description; }
    public void setStatus(Integer status) { this.status = status; }
    public void setEditable(Integer editable) { this.editable = editable; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
