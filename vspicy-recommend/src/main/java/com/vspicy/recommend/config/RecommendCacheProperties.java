package com.vspicy.recommend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vspicy.recommend.cache")
public class RecommendCacheProperties {
    private boolean enabled = true;
    private long feedTtlSeconds = 60;
    private long hotTtlSeconds = 120;
    private long similarTtlSeconds = 300;
    private long staleTtlSeconds = 1800;

    public boolean isEnabled() {
        return enabled;
    }

    public long getFeedTtlSeconds() {
        return feedTtlSeconds;
    }

    public long getHotTtlSeconds() {
        return hotTtlSeconds;
    }

    public long getSimilarTtlSeconds() {
        return similarTtlSeconds;
    }

    public long getStaleTtlSeconds() {
        return staleTtlSeconds;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setFeedTtlSeconds(long feedTtlSeconds) {
        this.feedTtlSeconds = feedTtlSeconds;
    }

    public void setHotTtlSeconds(long hotTtlSeconds) {
        this.hotTtlSeconds = hotTtlSeconds;
    }

    public void setSimilarTtlSeconds(long similarTtlSeconds) {
        this.similarTtlSeconds = similarTtlSeconds;
    }

    public void setStaleTtlSeconds(long staleTtlSeconds) {
        this.staleTtlSeconds = staleTtlSeconds;
    }
}
