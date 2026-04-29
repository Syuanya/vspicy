package com.vspicy.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "vspicy.gateway.auth")
public class GatewayAuthProperties {
    private boolean enabled = true;
    private boolean devBypassEnabled = true;
    private Long defaultUserId = 1L;
    private String defaultUsername = "dev-admin";
    private List<String> defaultRoles = new ArrayList<>();
    private List<String> defaultPermissions = new ArrayList<>();
    private List<String> whitelist = new ArrayList<>();
    private List<PermissionRule> permissionRules = new ArrayList<>();

    public GatewayAuthProperties() {
        defaultRoles.add("SUPER_ADMIN");
        defaultPermissions.add("*");
    }

    public boolean isEnabled() { return enabled; }
    public boolean isDevBypassEnabled() { return devBypassEnabled; }
    public Long getDefaultUserId() { return defaultUserId; }
    public String getDefaultUsername() { return defaultUsername; }
    public List<String> getDefaultRoles() { return defaultRoles; }
    public List<String> getDefaultPermissions() { return defaultPermissions; }
    public List<String> getWhitelist() { return whitelist; }
    public List<PermissionRule> getPermissionRules() { return permissionRules; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setDevBypassEnabled(boolean devBypassEnabled) { this.devBypassEnabled = devBypassEnabled; }
    public void setDefaultUserId(Long defaultUserId) { this.defaultUserId = defaultUserId; }
    public void setDefaultUsername(String defaultUsername) { this.defaultUsername = defaultUsername; }
    public void setDefaultRoles(List<String> defaultRoles) { this.defaultRoles = defaultRoles; }
    public void setDefaultPermissions(List<String> defaultPermissions) { this.defaultPermissions = defaultPermissions; }
    public void setWhitelist(List<String> whitelist) { this.whitelist = whitelist; }
    public void setPermissionRules(List<PermissionRule> permissionRules) { this.permissionRules = permissionRules; }

    public static class PermissionRule {
        private String path;
        private List<String> methods = new ArrayList<>();
        private String permission;

        public String getPath() { return path; }
        public List<String> getMethods() { return methods; }
        public String getPermission() { return permission; }
        public void setPath(String path) { this.path = path; }
        public void setMethods(List<String> methods) { this.methods = methods; }
        public void setPermission(String permission) { this.permission = permission; }
    }
}
