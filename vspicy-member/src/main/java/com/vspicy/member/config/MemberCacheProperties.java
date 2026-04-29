package com.vspicy.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vspicy.member.cache")
public class MemberCacheProperties {
    private boolean enabled = true;
    private long membershipTtlSeconds = 1800;

    public boolean isEnabled() {
        return enabled;
    }

    public long getMembershipTtlSeconds() {
        return membershipTtlSeconds;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setMembershipTtlSeconds(long membershipTtlSeconds) {
        this.membershipTtlSeconds = membershipTtlSeconds;
    }
}
