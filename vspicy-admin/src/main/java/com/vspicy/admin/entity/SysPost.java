package com.vspicy.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_post")
public class SysPost {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String postCode;
    private String postName;
    private Integer sortNo;
    private Integer status;
    private Integer editable;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getPostCode() { return postCode; }
    public String getPostName() { return postName; }
    public Integer getSortNo() { return sortNo; }
    public Integer getStatus() { return status; }
    public Integer getEditable() { return editable; }
    public String getRemark() { return remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setId(Long id) { this.id = id; }
    public void setPostCode(String postCode) { this.postCode = postCode; }
    public void setPostName(String postName) { this.postName = postName; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public void setStatus(Integer status) { this.status = status; }
    public void setEditable(Integer editable) { this.editable = editable; }
    public void setRemark(String remark) { this.remark = remark; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
