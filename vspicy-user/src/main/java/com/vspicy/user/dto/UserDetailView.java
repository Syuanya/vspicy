package com.vspicy.user.dto;

import com.vspicy.user.entity.SysUser;

public record UserDetailView(
        SysUser user,
        UserOverviewView overview
) {
}
