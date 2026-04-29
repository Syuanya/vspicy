package com.vspicy.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vspicy.notification.mq")
public class NotificationMqProperties {
    private boolean enabled = false;
    private boolean fallbackToSync = true;
    private String topic = "vspicy-notification-event-topic";
    private String producerGroup = "vspicy-notification-producer";
    private String consumerGroup = "vspicy-notification-consumer";
    private boolean autoRetryEnabled = false;
    private long retryFixedDelayMs = 60000;
    private int retryScanLimit = 20;
    private int maxRetryCount = 5;
    private boolean retryLockEnabled = true;
    private String retryLockKey = "notification_event_auto_retry";
    private int retryLockLeaseSeconds = 120;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isFallbackToSync() {
        return fallbackToSync;
    }

    public String getTopic() {
        return topic;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public boolean isAutoRetryEnabled() {
        return autoRetryEnabled;
    }

    public long getRetryFixedDelayMs() {
        return retryFixedDelayMs;
    }

    public int getRetryScanLimit() {
        return retryScanLimit;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public boolean isRetryLockEnabled() {
        return retryLockEnabled;
    }

    public String getRetryLockKey() {
        return retryLockKey;
    }

    public int getRetryLockLeaseSeconds() {
        return retryLockLeaseSeconds;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setFallbackToSync(boolean fallbackToSync) {
        this.fallbackToSync = fallbackToSync;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public void setAutoRetryEnabled(boolean autoRetryEnabled) {
        this.autoRetryEnabled = autoRetryEnabled;
    }

    public void setRetryFixedDelayMs(long retryFixedDelayMs) {
        this.retryFixedDelayMs = retryFixedDelayMs;
    }

    public void setRetryScanLimit(int retryScanLimit) {
        this.retryScanLimit = retryScanLimit;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public void setRetryLockEnabled(boolean retryLockEnabled) {
        this.retryLockEnabled = retryLockEnabled;
    }

    public void setRetryLockKey(String retryLockKey) {
        this.retryLockKey = retryLockKey;
    }

    public void setRetryLockLeaseSeconds(int retryLockLeaseSeconds) {
        this.retryLockLeaseSeconds = retryLockLeaseSeconds;
    }
}
