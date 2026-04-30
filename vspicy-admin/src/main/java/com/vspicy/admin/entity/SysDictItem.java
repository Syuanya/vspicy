package com.vspicy.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_dict_item")
public class SysDictItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String typeCode;
    private String itemLabel;
    private String itemValue;
    private Integer sortNo;
    private String cssClass;
    private String extraJson;
    private Integer status;
    private Integer editable;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getTypeCode() { return typeCode; }
    public String getItemLabel() { return itemLabel; }
    public String getItemValue() { return itemValue; }
    public Integer getSortNo() { return sortNo; }
    public String getCssClass() { return cssClass; }
    public String getExtraJson() { return extraJson; }
    public Integer getStatus() { return status; }
    public Integer getEditable() { return editable; }
    public String getRemark() { return remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public void setItemLabel(String itemLabel) { this.itemLabel = itemLabel; }
    public void setItemValue(String itemValue) { this.itemValue = itemValue; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public void setCssClass(String cssClass) { this.cssClass = cssClass; }
    public void setExtraJson(String extraJson) { this.extraJson = extraJson; }
    public void setStatus(Integer status) { this.status = status; }
    public void setEditable(Integer editable) { this.editable = editable; }
    public void setRemark(String remark) { this.remark = remark; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
