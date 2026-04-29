package com.vspicy.video.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.dto.VideoUploadTraceLinkCommand;
import com.vspicy.video.dto.VideoUploadTraceView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class VideoUploadTraceService {
    private final JdbcTemplate jdbcTemplate;

    public VideoUploadTraceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<VideoUploadTraceView> list(String keyword, Integer limit) {
        int safeLimit = normalizeLimit(limit);
        String safeKeyword = keyword == null ? "" : keyword.trim();

        if (safeKeyword.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT id, trace_id, user_id, video_id, record_id, upload_task_id,
                           bucket, object_key, file_name, size_mb, status, source, remark,
                           created_at, updated_at
                    FROM video_upload_trace
                    ORDER BY id DESC
                    LIMIT ?
                    """, (rs, rowNum) -> mapTrace(rs), safeLimit);
        }

        String like = "%" + safeKeyword + "%";
        Long number = tryParseLong(safeKeyword);

        return jdbcTemplate.query("""
                SELECT id, trace_id, user_id, video_id, record_id, upload_task_id,
                       bucket, object_key, file_name, size_mb, status, source, remark,
                       created_at, updated_at
                FROM video_upload_trace
                WHERE trace_id LIKE ?
                   OR upload_task_id LIKE ?
                   OR object_key LIKE ?
                   OR file_name LIKE ?
                   OR (? IS NOT NULL AND user_id = ?)
                   OR (? IS NOT NULL AND video_id = ?)
                   OR (? IS NOT NULL AND record_id = ?)
                ORDER BY id DESC
                LIMIT ?
                """, (rs, rowNum) -> mapTrace(rs),
                like, like, like, like,
                number, number,
                number, number,
                number, number,
                safeLimit);
    }

    public List<VideoUploadTraceView> byVideo(Long videoId) {
        if (videoId == null) {
            throw new BizException("videoId 不能为空");
        }

        return jdbcTemplate.query("""
                SELECT id, trace_id, user_id, video_id, record_id, upload_task_id,
                       bucket, object_key, file_name, size_mb, status, source, remark,
                       created_at, updated_at
                FROM video_upload_trace
                WHERE video_id = ?
                ORDER BY id DESC
                """, (rs, rowNum) -> mapTrace(rs), videoId);
    }

    public List<VideoUploadTraceView> byRecord(Long recordId) {
        if (recordId == null) {
            throw new BizException("recordId 不能为空");
        }

        return jdbcTemplate.query("""
                SELECT id, trace_id, user_id, video_id, record_id, upload_task_id,
                       bucket, object_key, file_name, size_mb, status, source, remark,
                       created_at, updated_at
                FROM video_upload_trace
                WHERE record_id = ?
                ORDER BY id DESC
                """, (rs, rowNum) -> mapTrace(rs), recordId);
    }

    public List<VideoUploadTraceView> byTask(String uploadTaskId) {
        if (uploadTaskId == null || uploadTaskId.isBlank()) {
            throw new BizException("uploadTaskId 不能为空");
        }

        return jdbcTemplate.query("""
                SELECT id, trace_id, user_id, video_id, record_id, upload_task_id,
                       bucket, object_key, file_name, size_mb, status, source, remark,
                       created_at, updated_at
                FROM video_upload_trace
                WHERE upload_task_id = ?
                ORDER BY id DESC
                """, (rs, rowNum) -> mapTrace(rs), uploadTaskId);
    }

    @Transactional
    public VideoUploadTraceView link(VideoUploadTraceLinkCommand command, Long headerUserId) {
        if (command == null) {
            throw new BizException("请求不能为空");
        }

        Long userId = command.userId() != null ? command.userId() : normalizeUserId(headerUserId);

        if (command.recordId() == null && command.videoId() == null && isBlank(command.uploadTaskId()) && isBlank(command.objectKey())) {
            throw new BizException("recordId / videoId / uploadTaskId / objectKey 至少填写一个");
        }

        RecordSnapshot snapshot = command.recordId() == null ? null : findRecord(command.recordId(), userId);

        Long videoId = firstNonNull(command.videoId(), snapshot == null ? null : snapshot.videoId());
        String uploadTaskId = firstNonBlank(command.uploadTaskId(), snapshot == null ? null : snapshot.uploadTaskId());
        String bucket = firstNonBlank(command.bucket(), snapshot == null ? null : snapshot.bucket(), "vspicy");
        String objectKey = firstNonBlank(command.objectKey(), snapshot == null ? null : snapshot.objectKey());
        String fileName = firstNonBlank(command.fileName(), snapshot == null ? null : snapshot.fileName());
        Long sizeMb = firstNonNull(command.sizeMb(), snapshot == null ? null : snapshot.sizeMb(), 0L);
        String source = firstNonBlank(command.source(), "MANUAL_BIND");
        String remark = command.remark();

        String traceId = "TR" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        jdbcTemplate.update("""
                INSERT INTO video_upload_trace(
                  trace_id, user_id, video_id, record_id, upload_task_id,
                  bucket, object_key, file_name, size_mb, status, source, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'BOUND', ?, ?)
                """, traceId, userId, videoId, command.recordId(), uploadTaskId,
                bucket, objectKey, fileName, sizeMb, source, remark);

        if (command.recordId() != null) {
            jdbcTemplate.update("""
                    UPDATE video_upload_record
                    SET video_id = COALESCE(?, video_id),
                        upload_task_id = COALESCE(?, upload_task_id),
                        bucket = COALESCE(?, bucket),
                        object_key = COALESCE(?, object_key),
                        trace_id = ?,
                        confirm_source = ?,
                        bound_at = NOW(),
                        remark = COALESCE(?, remark)
                    WHERE id = ?
                      AND user_id = ?
                    """, videoId, uploadTaskId, bucket, objectKey, traceId, source, remark, command.recordId(), userId);
        }

        return byTraceId(traceId);
    }

    private VideoUploadTraceView byTraceId(String traceId) {
        List<VideoUploadTraceView> rows = jdbcTemplate.query("""
                SELECT id, trace_id, user_id, video_id, record_id, upload_task_id,
                       bucket, object_key, file_name, size_mb, status, source, remark,
                       created_at, updated_at
                FROM video_upload_trace
                WHERE trace_id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapTrace(rs), traceId);

        if (rows.isEmpty()) {
            throw new BizException("上传追踪记录创建失败");
        }

        return rows.get(0);
    }

    private RecordSnapshot findRecord(Long recordId, Long userId) {
        List<RecordSnapshot> rows = jdbcTemplate.query("""
                SELECT id, user_id, video_id, file_name, size_mb, status,
                       upload_task_id, bucket, object_key, trace_id
                FROM video_upload_record
                WHERE id = ?
                  AND user_id = ?
                LIMIT 1
                """, (rs, rowNum) -> new RecordSnapshot(
                rs.getLong("id"),
                rs.getLong("user_id"),
                nullableLong(rs.getObject("video_id")),
                rs.getString("file_name"),
                rs.getLong("size_mb"),
                rs.getString("status"),
                rs.getString("upload_task_id"),
                rs.getString("bucket"),
                rs.getString("object_key"),
                rs.getString("trace_id")
        ), recordId, userId);

        if (rows.isEmpty()) {
            throw new BizException("上传记录不存在或无权绑定");
        }

        return rows.get(0);
    }

    private VideoUploadTraceView mapTrace(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new VideoUploadTraceView(
                rs.getLong("id"),
                rs.getString("trace_id"),
                rs.getLong("user_id"),
                nullableLong(rs.getObject("video_id")),
                nullableLong(rs.getObject("record_id")),
                rs.getString("upload_task_id"),
                rs.getString("bucket"),
                rs.getString("object_key"),
                rs.getString("file_name"),
                rs.getLong("size_mb"),
                rs.getString("status"),
                rs.getString("source"),
                rs.getString("remark"),
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

    private Long tryParseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record RecordSnapshot(
            Long id,
            Long userId,
            Long videoId,
            String fileName,
            Long sizeMb,
            String status,
            String uploadTaskId,
            String bucket,
            String objectKey,
            String traceId
    ) {
    }
}
