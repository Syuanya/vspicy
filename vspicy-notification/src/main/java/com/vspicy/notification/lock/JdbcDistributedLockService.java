package com.vspicy.notification.lock;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.UUID;

@Service
public class JdbcDistributedLockService {
    private final JdbcTemplate jdbcTemplate;
    private final String ownerId;

    public JdbcDistributedLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.ownerId = buildOwnerId();
    }

    public boolean tryLock(String lockKey, int leaseSeconds) {
        if (lockKey == null || lockKey.isBlank()) {
            return false;
        }

        int safeLeaseSeconds = leaseSeconds <= 0 ? 60 : leaseSeconds;

        try {
            jdbcTemplate.update("""
                    INSERT INTO distributed_lock(lock_key, owner_id, lease_until)
                    VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? SECOND))
                    """, lockKey, ownerId, safeLeaseSeconds);
            return true;
        } catch (DuplicateKeyException duplicateKeyException) {
            return tryUpdateExistingLock(lockKey, safeLeaseSeconds);
        }
    }

    public void unlock(String lockKey) {
        if (lockKey == null || lockKey.isBlank()) {
            return;
        }

        jdbcTemplate.update("""
                DELETE FROM distributed_lock
                WHERE lock_key = ?
                  AND owner_id = ?
                """, lockKey, ownerId);
    }

    public String ownerId() {
        return ownerId;
    }

    private boolean tryUpdateExistingLock(String lockKey, int leaseSeconds) {
        int updated = jdbcTemplate.update("""
                UPDATE distributed_lock
                SET owner_id = ?,
                    lease_until = DATE_ADD(NOW(), INTERVAL ? SECOND)
                WHERE lock_key = ?
                  AND (
                    owner_id = ?
                    OR lease_until < NOW()
                  )
                """, ownerId, leaseSeconds, lockKey, ownerId);

        return updated > 0;
    }

    private String buildOwnerId() {
        String host = "unknown-host";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // ignore
        }

        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        return host + ":" + runtimeName + ":" + UUID.randomUUID();
    }
}
