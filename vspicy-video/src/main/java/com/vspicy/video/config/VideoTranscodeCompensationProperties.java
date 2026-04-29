package com.vspicy.video.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vspicy.video.transcode.compensation")
public class VideoTranscodeCompensationProperties {
    private Boolean enabled = true;

    private Long fixedDelayMs = 60000L;
    private Long initialDelayMs = 30000L;

    private Integer scanLimit = 20;
    private Integer batchSize = 20;
    private Integer limit = 20;

    /**
     * 各状态单次扫描数量，兼容历史补偿代码。
     */
    private Integer pendingLimit = 20;
    private Integer processingLimit = 20;
    private Integer runningLimit = 20;
    private Integer failedLimit = 20;
    private Integer deadLimit = 20;
    private Integer compensateLimit = 20;

    private Integer maxRetryCount = 3;

    private Integer stuckMinutes = 10;
    private Integer processingTimeoutMinutes = 30;
    private Integer pendingTimeoutMinutes = 30;
    private Integer runningTimeoutMinutes = 60;
    private Integer timeoutMinutes = 30;

    private Integer retryDelaySeconds = 60;

    private Boolean lockEnabled = false;
    private String lockKey = "video_transcode_compensation";
    private Integer lockLeaseSeconds = 120;

    private String cron = "0 */1 * * * ?";

    public Boolean getEnabled() {
        return enabled;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getCompensationEnabled() {
        return enabled;
    }

    public boolean isCompensationEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public void setCompensationEnabled(Boolean compensationEnabled) {
        this.enabled = compensationEnabled;
    }

    public Long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(Long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    public Long getFixedDelay() {
        return fixedDelayMs;
    }

    public void setFixedDelay(Long fixedDelay) {
        this.fixedDelayMs = fixedDelay;
    }

    public Long getInitialDelayMs() {
        return initialDelayMs;
    }

    public void setInitialDelayMs(Long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }

    public Long getInitialDelay() {
        return initialDelayMs;
    }

    public void setInitialDelay(Long initialDelay) {
        this.initialDelayMs = initialDelay;
    }

    public Integer getScanLimit() {
        return scanLimit;
    }

    public void setScanLimit(Integer scanLimit) {
        this.scanLimit = scanLimit;
        setAllLimits(scanLimit);
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
        this.scanLimit = limit;
        setAllLimits(limit);
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
        this.scanLimit = batchSize;
        this.limit = batchSize;
        setAllLimits(batchSize);
    }

    public Integer getPendingLimit() {
        return pendingLimit;
    }

    public void setPendingLimit(Integer pendingLimit) {
        this.pendingLimit = pendingLimit;
    }

    public Integer getProcessingLimit() {
        return processingLimit;
    }

    public void setProcessingLimit(Integer processingLimit) {
        this.processingLimit = processingLimit;
    }

    public Integer getRunningLimit() {
        return runningLimit;
    }

    public void setRunningLimit(Integer runningLimit) {
        this.runningLimit = runningLimit;
    }

    public Integer getFailedLimit() {
        return failedLimit;
    }

    public void setFailedLimit(Integer failedLimit) {
        this.failedLimit = failedLimit;
    }

    public Integer getDeadLimit() {
        return deadLimit;
    }

    public void setDeadLimit(Integer deadLimit) {
        this.deadLimit = deadLimit;
    }

    public Integer getCompensateLimit() {
        return compensateLimit;
    }

    public void setCompensateLimit(Integer compensateLimit) {
        this.compensateLimit = compensateLimit;
    }

    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public Integer getMaxRetries() {
        return maxRetryCount;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetryCount = maxRetries;
    }

    public Integer getRetryCount() {
        return maxRetryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.maxRetryCount = retryCount;
    }

    public Integer getStuckMinutes() {
        return stuckMinutes;
    }

    public void setStuckMinutes(Integer stuckMinutes) {
        this.stuckMinutes = stuckMinutes;
    }

    public Integer getStuckTimeoutMinutes() {
        return stuckMinutes;
    }

    public void setStuckTimeoutMinutes(Integer stuckTimeoutMinutes) {
        this.stuckMinutes = stuckTimeoutMinutes;
    }

    public Integer getProcessingTimeoutMinutes() {
        return processingTimeoutMinutes;
    }

    public void setProcessingTimeoutMinutes(Integer processingTimeoutMinutes) {
        this.processingTimeoutMinutes = processingTimeoutMinutes;
        this.timeoutMinutes = processingTimeoutMinutes;
    }

    public Integer getPendingTimeoutMinutes() {
        return pendingTimeoutMinutes;
    }

    public void setPendingTimeoutMinutes(Integer pendingTimeoutMinutes) {
        this.pendingTimeoutMinutes = pendingTimeoutMinutes;
    }

    public Integer getRunningTimeoutMinutes() {
        return runningTimeoutMinutes;
    }

    public void setRunningTimeoutMinutes(Integer runningTimeoutMinutes) {
        this.runningTimeoutMinutes = runningTimeoutMinutes;
    }

    public Integer getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(Integer timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
        this.processingTimeoutMinutes = timeoutMinutes;
        this.pendingTimeoutMinutes = timeoutMinutes;
        this.runningTimeoutMinutes = timeoutMinutes;
    }

    public Integer getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    public void setRetryDelaySeconds(Integer retryDelaySeconds) {
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public Integer getRetryIntervalSeconds() {
        return retryDelaySeconds;
    }

    public void setRetryIntervalSeconds(Integer retryIntervalSeconds) {
        this.retryDelaySeconds = retryIntervalSeconds;
    }

    public Integer getRetryDelay() {
        return retryDelaySeconds;
    }

    public void setRetryDelay(Integer retryDelay) {
        this.retryDelaySeconds = retryDelay;
    }

    public Boolean getLockEnabled() {
        return lockEnabled;
    }

    public boolean isLockEnabled() {
        return Boolean.TRUE.equals(lockEnabled);
    }

    public void setLockEnabled(Boolean lockEnabled) {
        this.lockEnabled = lockEnabled;
    }

    public String getLockKey() {
        return lockKey;
    }

    public void setLockKey(String lockKey) {
        this.lockKey = lockKey;
    }

    public Integer getLockLeaseSeconds() {
        return lockLeaseSeconds;
    }

    public void setLockLeaseSeconds(Integer lockLeaseSeconds) {
        this.lockLeaseSeconds = lockLeaseSeconds;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    private void setAllLimits(Integer value) {
        if (value == null) {
            return;
        }
        this.pendingLimit = value;
        this.processingLimit = value;
        this.runningLimit = value;
        this.failedLimit = value;
        this.deadLimit = value;
        this.compensateLimit = value;
    }
}
