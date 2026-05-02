package com.vspicy.admin.controller;

import com.vspicy.admin.audit.AuditLog;
import com.vspicy.admin.dto.DeptCommand;
import com.vspicy.admin.dto.DeptView;
import com.vspicy.admin.dto.OrgOverviewView;
import com.vspicy.admin.dto.PostCommand;
import com.vspicy.admin.dto.PostView;
import com.vspicy.admin.dto.UserOrgAssignCommand;
import com.vspicy.admin.dto.UserOrgView;
import com.vspicy.admin.service.OrgService;
import com.vspicy.common.core.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/org")
public class OrgController {
    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @GetMapping("/overview")
    public Result<OrgOverviewView> overview() {
        return Result.ok(orgService.overview());
    }

    @GetMapping("/depts")
    public Result<List<DeptView>> depts(@RequestParam(value = "keyword", required = false) String keyword,
                                        @RequestParam(value = "status", required = false) Integer status) {
        return Result.ok(orgService.deptTree(keyword, status));
    }

    @GetMapping("/depts/{id}")
    public Result<DeptView> getDept(@PathVariable("id") Long id) {
        return Result.ok(orgService.getDept(id));
    }

    @AuditLog(type = "CREATE", title = "创建部门")
    @PostMapping("/depts")
    public Result<DeptView> createDept(@RequestBody DeptCommand command) {
        return Result.ok(orgService.createDept(command));
    }

    @AuditLog(type = "UPDATE", title = "更新部门")
    @PutMapping("/depts/{id}")
    public Result<DeptView> updateDept(@PathVariable("id") Long id, @RequestBody DeptCommand command) {
        return Result.ok(orgService.updateDept(id, command));
    }

    @AuditLog(type = "UPDATE", title = "启用部门")
    @PostMapping("/depts/{id}/enable")
    public Result<DeptView> enableDept(@PathVariable("id") Long id) {
        return Result.ok(orgService.enableDept(id));
    }

    @AuditLog(type = "UPDATE", title = "停用部门")
    @PostMapping("/depts/{id}/disable")
    public Result<DeptView> disableDept(@PathVariable("id") Long id) {
        return Result.ok(orgService.disableDept(id));
    }

    @AuditLog(type = "DELETE", title = "删除部门")
    @DeleteMapping("/depts/{id}")
    public Result<Void> deleteDept(@PathVariable("id") Long id) {
        orgService.deleteDept(id);
        return Result.ok();
    }

    @GetMapping("/posts")
    public Result<List<PostView>> posts(@RequestParam(value = "keyword", required = false) String keyword,
                                        @RequestParam(value = "status", required = false) Integer status) {
        return Result.ok(orgService.posts(keyword, status));
    }

    @GetMapping("/posts/{id}")
    public Result<PostView> getPost(@PathVariable("id") Long id) {
        return Result.ok(orgService.getPost(id));
    }

    @AuditLog(type = "CREATE", title = "创建岗位")
    @PostMapping("/posts")
    public Result<PostView> createPost(@RequestBody PostCommand command) {
        return Result.ok(orgService.createPost(command));
    }

    @AuditLog(type = "UPDATE", title = "更新岗位")
    @PutMapping("/posts/{id}")
    public Result<PostView> updatePost(@PathVariable("id") Long id, @RequestBody PostCommand command) {
        return Result.ok(orgService.updatePost(id, command));
    }

    @AuditLog(type = "UPDATE", title = "启用岗位")
    @PostMapping("/posts/{id}/enable")
    public Result<PostView> enablePost(@PathVariable("id") Long id) {
        return Result.ok(orgService.enablePost(id));
    }

    @AuditLog(type = "UPDATE", title = "停用岗位")
    @PostMapping("/posts/{id}/disable")
    public Result<PostView> disablePost(@PathVariable("id") Long id) {
        return Result.ok(orgService.disablePost(id));
    }

    @AuditLog(type = "DELETE", title = "删除岗位")
    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(@PathVariable("id") Long id) {
        orgService.deletePost(id);
        return Result.ok();
    }

    @GetMapping("/users/{userId}")
    public Result<UserOrgView> userOrg(@PathVariable("userId") Long userId) {
        return Result.ok(orgService.userOrg(userId));
    }

    @AuditLog(type = "UPDATE", title = "分配用户组织岗位")
    @PostMapping("/users/{userId}")
    public Result<UserOrgView> assignUserOrg(@PathVariable("userId") Long userId,
                                             @RequestBody UserOrgAssignCommand command) {
        return Result.ok(orgService.assignUserOrg(userId, command));
    }
}
