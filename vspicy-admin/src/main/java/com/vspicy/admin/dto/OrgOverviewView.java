package com.vspicy.admin.dto;

public record OrgOverviewView(
        long deptCount,
        long enabledDeptCount,
        long disabledDeptCount,
        long postCount,
        long enabledPostCount,
        long disabledPostCount,
        long userDeptRelationCount,
        long userPostRelationCount
) {
}
