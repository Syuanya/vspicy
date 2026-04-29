package com.vspicy.video.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.dto.*;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ObjectCleanupApprovalService {
    private final JdbcTemplate jdbcTemplate;
    private final VideoStorageScanService storageScanService;
    private final MinioClient minioClient;

    public ObjectCleanupApprovalService(
            JdbcTemplate jdbcTemplate,
            VideoStorageScanService storageScanService,
            MinioClient minioClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageScanService = storageScanService;
        this.minioClient = minioClient;
    }

    public List<ObjectCleanupRequestView> list(String status, Integer limit) {
        String safeStatus = status == null ? "" : status.trim();
        int safeLimit = normalizeLimit(limit);

        if (safeStatus.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT id, dedup_key, bucket, object_key, object_size, issue_type,
                           source, status, reason, approve_user_id, reject_user_id,
                           execute_user_id, error_message, approved_at, rejected_at,
                           executed_at, created_at, updated_at
                    FROM video_object_cleanup_request
                    ORDER BY
                      CASE status
                        WHEN 'PENDING' THEN 1
                        WHEN 'APPROVED' THEN 2
                        WHEN 'FAILED' THEN 3
                        ELSE 4
                      END,
                      id DESC
                    LIMIT ?
                    """, (rs, rowNum) -> mapRequest(rs), safeLimit);
        }

        return jdbcTemplate.query("""
                SELECT id, dedup_key, bucket, object_key, object_size, issue_type,
                       source, status, reason, approve_user_id, reject_user_id,
                       execute_user_id, error_message, approved_at, rejected_at,
                       executed_at, created_at, updated_at
                FROM video_object_cleanup_request
                WHERE status = ?
                ORDER BY id DESC
                LIMIT ?
                """, (rs, rowNum) -> mapRequest(rs), safeStatus, safeLimit);
    }

    @Transactional
    public ObjectCleanupGenerateResult generate(ObjectCleanupGenerateCommand command) {
        String prefix = command == null || command.prefix() == null || command.prefix().isBlank()
                ? "videos/"
                : command.prefix();
        int limit = command == null || command.limit() == null ? 1000 : normalizeLargeLimit(command.limit());

        VideoStorageScanResult scan = storageScanService.scan(prefix, limit);
        long scanned = 0L;
        long created = 0L;
        long existing = 0L;

        for (VideoStorageScanItem item : scan.items()) {
            if (!"OBJECT_MISSING_DB".equals(item.issueType())) {
                continue;
            }

            scanned++;
            String dedupKey = scan.bucket() + ":" + item.objectKey();

            int affected = jdbcTemplate.update("""
                    INSERT IGNORE INTO video_object_cleanup_request(
                      dedup_key, bucket, object_key, object_size, issue_type, source,
                      status, reason
                    )
                    VALUES (?, ?, ?, ?, 'OBJECT_MISSING_DB', 'STORAGE_SCAN', 'PENDING', ?)
                    """, dedupKey, scan.bucket(), item.objectKey(), item.size(), item.message());

            if (affected > 0) {
                created++;
            } else {
                existing++;
            }
        }

        return new ObjectCleanupGenerateResult(
                prefix,
                limit,
                scanned,
                created,
                existing,
                countByStatus("PENDING"),
                "对象清理申请生成完成"
        );
    }

    @Transactional
    public ObjectCleanupRequestView approve(Long id, Long userId) {
        if (id == null) {
            throw new BizException("id 不能为空");
        }

        int updated = jdbcTemplate.update("""
                UPDATE video_object_cleanup_request
                SET status = 'APPROVED',
                    approve_user_id = ?,
                    approved_at = NOW(),
                    error_message = NULL
                WHERE id = ?
                  AND status IN ('PENDING', 'FAILED')
                """, normalizeUserId(userId), id);

        if (updated <= 0) {
            throw new BizException("清理申请不存在或当前状态不可审批");
        }

        return findById(id);
    }

    @Transactional
    public ObjectCleanupRequestView reject(Long id, Long userId) {
        if (id == null) {
            throw new BizException("id 不能为空");
        }

        int updated = jdbcTemplate.update("""
                UPDATE video_object_cleanup_request
                SET status = 'REJECTED',
                    reject_user_id = ?,
                    rejected_at = NOW()
                WHERE id = ?
                  AND status IN ('PENDING', 'APPROVED', 'FAILED')
                """, normalizeUserId(userId), id);

        if (updated <= 0) {
            throw new BizException("清理申请不存在或当前状态不可拒绝");
        }

        return findById(id);
    }

    @Transactional
    public ObjectCleanupExecuteResult executeOne(Long id, ObjectCleanupExecuteCommand command, Long userId) {
        if (id == null) {
            throw new BizException("id 不能为空");
        }

        boolean dryRun = command == null || command.dryRun() == null || Boolean.TRUE.equals(command.dryRun());
        ObjectCleanupRequestView request = findById(id);
        return executeRequests(List.of(request), dryRun, userId);
    }

    @Transactional
    public ObjectCleanupExecuteResult executeApproved(ObjectCleanupExecuteCommand command, Long userId) {
        int limit = command == null || command.limit() == null ? 20 : normalizeLimit(command.limit());
        boolean dryRun = command == null || command.dryRun() == null || Boolean.TRUE.equals(command.dryRun());

        List<ObjectCleanupRequestView> requests = jdbcTemplate.query("""
                SELECT id, dedup_key, bucket, object_key, object_size, issue_type,
                       source, status, reason, approve_user_id, reject_user_id,
                       execute_user_id, error_message, approved_at, rejected_at,
                       executed_at, created_at, updated_at
                FROM video_object_cleanup_request
                WHERE status = 'APPROVED'
                ORDER BY id ASC
                LIMIT ?
                """, (rs, rowNum) -> mapRequest(rs), limit);

        return executeRequests(requests, dryRun, userId);
    }

    private ObjectCleanupExecuteResult executeRequests(
            List<ObjectCleanupRequestView> requests,
            boolean dryRun,
            Long userId
    ) {
        long deleted = 0L;
        long skipped = 0L;
        long failed = 0L;
        List<ObjectCleanupRequestView> resultItems = new ArrayList<>();

        for (ObjectCleanupRequestView request : requests) {
            if (!"APPROVED".equals(request.status())) {
                skipped++;
                resultItems.add(request);
                continue;
            }

            if (dryRun) {
                skipped++;
                resultItems.add(request);
                continue;
            }

            try {
                if (objectExists(request.bucket(), request.objectKey())) {
                    minioClient.removeObject(
                            RemoveObjectArgs.builder()
                                    .bucket(request.bucket())
                                    .object(request.objectKey())
                                    .build()
                    );
                }

                jdbcTemplate.update("""
                        UPDATE video_object_cleanup_request
                        SET status = 'EXECUTED',
                            execute_user_id = ?,
                            executed_at = NOW(),
                            error_message = NULL
                        WHERE id = ?
                        """, normalizeUserId(userId), request.id());

                deleted++;
                resultItems.add(findById(request.id()));
            } catch (Exception ex) {
                failed++;
                jdbcTemplate.update("""
                        UPDATE video_object_cleanup_request
                        SET status = 'FAILED',
                            error_message = ?
                        WHERE id = ?
                        """, ex.getMessage(), request.id());
                resultItems.add(findById(request.id()));
            }
        }

        return new ObjectCleanupExecuteResult(
                dryRun,
                (long) requests.size(),
                deleted,
                skipped,
                failed,
                dryRun ? "dryRun=true，仅预览未删除" : "对象清理执行完成",
                resultItems
        );
    }

    private boolean objectExists(String bucket, String objectKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private ObjectCleanupRequestView findById(Long id) {
        List<ObjectCleanupRequestView> rows = jdbcTemplate.query("""
                SELECT id, dedup_key, bucket, object_key, object_size, issue_type,
                       source, status, reason, approve_user_id, reject_user_id,
                       execute_user_id, error_message, approved_at, rejected_at,
                       executed_at, created_at, updated_at
                FROM video_object_cleanup_request
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapRequest(rs), id);

        if (rows.isEmpty()) {
            throw new BizException("对象清理申请不存在");
        }

        return rows.get(0);
    }

    private long countByStatus(String status) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM video_object_cleanup_request
                WHERE status = ?
                """, Long.class, status);
        return value == null ? 0L : value;
    }

    private ObjectCleanupRequestView mapRequest(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ObjectCleanupRequestView(
                rs.getLong("id"),
                rs.getString("dedup_key"),
                rs.getString("bucket"),
                rs.getString("object_key"),
                nullableLong(rs.getObject("object_size")),
                rs.getString("issue_type"),
                rs.getString("source"),
                rs.getString("status"),
                rs.getString("reason"),
                nullableLong(rs.getObject("approve_user_id")),
                nullableLong(rs.getObject("reject_user_id")),
                nullableLong(rs.getObject("execute_user_id")),
                rs.getString("error_message"),
                rs.getString("approved_at"),
                rs.getString("rejected_at"),
                rs.getString("executed_at"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }

    private Long normalizeUserId(Long userId) {
        return userId == null ? 1L : userId;
    }

    private int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 || limit > 500 ? 100 : limit;
    }

    private int normalizeLargeLimit(Integer limit) {
        return limit == null || limit <= 0 || limit > 10000 ? 1000 : limit;
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
