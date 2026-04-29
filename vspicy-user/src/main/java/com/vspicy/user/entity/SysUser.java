package com.vspicy.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String nickname;
    private String passwordHash;
    private String avatarUrl;
    private String email;
    private String phone;
    private Integer status;
    private Integer userType;
    private Integer deleted;

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getNickname() { return nickname; }
    public String getPasswordHash() { return passwordHash; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Integer getStatus() { return status; }
    public Integer getUserType() { return userType; }
    public Integer getDeleted() { return deleted; }

    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setStatus(Integer status) { this.status = status; }
    public void setUserType(Integer userType) { this.userType = userType; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
