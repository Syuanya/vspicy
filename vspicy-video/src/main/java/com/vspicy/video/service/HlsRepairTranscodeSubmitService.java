package com.vspicy.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vspicy.common.exception.BizException;
import com.vspicy.video.config.VideoStorageProperties;
import com.vspicy.video.entity.VideoTranscodeTask;
import com.vspicy.video.mapper.VideoTranscodeTaskMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

/**
 * HLS 修复任务精确接入现有 VideoTranscodeService。
 *
 * Phase55 增强：
 * 如果 video_transcode_task 缺失，会自动尝试补建一条 PENDING 转码任务。
 */
@Service
public class HlsRepairTranscodeSubmitService {
    private final JdbcTemplate jdbcTemplate;
    private final VideoTranscodeTaskMapper transcodeTaskMapper;
    private final VideoTranscodeService videoTranscodeService;
    private final VideoStorageProperties storageProperties;

    public HlsRepairTranscodeSubmitService(
            JdbcTemplate jdbcTemplate,
            VideoTranscodeTaskMapper transcodeTaskMapper,
            VideoTranscodeService videoTranscodeService,
            VideoStorageProperties storageProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transcodeTaskMapper = transcodeTaskMapper;
        this.videoTranscodeService = videoTranscodeService;
        this.storageProperties = storageProperties;
    }

    public void submitRepairTranscodeTask(Map<String, Object> payload) {
        Long repairTaskId = readLong(payload == null ? null : payload.get("taskId"));
        if (repairTaskId == null) {
            throw new BizException("HLS 修复执行失败：payload 缺少 taskId");
        }

        RepairTaskSnapshot repairTask = loadRepairTask(repairTaskId);
        Long videoId = repairTask.videoId() != null
                ? repairTask.videoId()
                : readLong(payload == null ? null : payload.get("videoId"));

        if (videoId == null) {
            throw new BizException("HLS 修复执行失败：repair task 未绑定 videoId，taskId=" + repairTaskId);
        }

        VideoTranscodeTask transcodeTask = findLatestTranscodeTask(videoId);
        boolean autoCreated = false;

        if (transcodeTask == null || transcodeTask.getId() == null) {
            Long createdTaskId = createMissingTranscodeTask(videoId, repairTask);
            transcodeTask = transcodeTaskMapper.selectById(createdTaskId);
            autoCreated = true;
        }

        if (transcodeTask == null || transcodeTask.getId() == null) {
            throw new BizException("HLS 修复执行失败：无法创建或读取转码任务，videoId=" + videoId);
        }

        resetTranscodeTask(transcodeTask);

        jdbcTemplate.update("""
                UPDATE video_hls_repair_task
                SET execute_mode = ?,
                    executor_bean = 'hlsRepairTranscodeSubmitService',
                    executor_method = 'submitRepairTranscodeTask',
                    last_error = NULL
                WHERE id = ?
                """, autoCreated
                ? "EXACT_VIDEO_TRANSCODE_SERVICE_AUTO_CREATED_TASK"
                : "EXACT_VIDEO_TRANSCODE_SERVICE", repairTaskId);

        videoTranscodeService.submitLocal(transcodeTask.getId());
    }

    private Long createMissingTranscodeTask(Long videoId, RepairTaskSnapshot repairTask) {
        if (!tableExists("video_transcode_task")) {
            throw new BizException("HLS 修复执行失败：video_transcode_task 表不存在");
        }

        TableColumns columns = columns("video_transcode_task");
        String videoIdColumn = columns.firstExisting("video_id", "videoId");
        String sourceFilePathColumn = columns.firstExisting("source_file_path", "sourceFilePath");
        String statusColumn = columns.firstExisting("status");
        String errorMessageColumn = columns.firstExisting("error_message", "errorMessage");
        String createdAtColumn = columns.firstExisting("created_at", "createdAt");
        String updatedAtColumn = columns.firstExisting("updated_at", "updatedAt");

        if (videoIdColumn == null) {
            throw new BizException("HLS 修复执行失败：video_transcode_task 缺少 video_id 字段");
        }

        if (sourceFilePathColumn == null) {
            throw new BizException("HLS 修复执行失败：video_transcode_task 缺少 source_file_path 字段");
        }

        String sourceFilePath = resolveSourceFilePath(videoId, repairTask);
        if (sourceFilePath == null || sourceFilePath.isBlank()) {
            throw new BizException("HLS 修复执行失败：未找到可用源视频本地路径，videoId=" + videoId);
        }

        List<String> insertColumns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        add(insertColumns, values, videoIdColumn, videoId);
        add(insertColumns, values, sourceFilePathColumn, sourceFilePath);

        if (statusColumn != null) {
            add(insertColumns, values, statusColumn, "PENDING");
        }
        if (errorMessageColumn != null) {
            add(insertColumns, values, errorMessageColumn, null);
        }
        if (createdAtColumn != null) {
            addRaw(insertColumns, createdAtColumn);
        }
        if (updatedAtColumn != null) {
            addRaw(insertColumns, updatedAtColumn);
        }

        String placeholders = buildPlaceholders(insertColumns.size(), createdAtColumn != null, updatedAtColumn != null);
        String sql = "INSERT INTO video_transcode_task("
                + String.join(",", insertColumns)
                + ") VALUES ("
                + placeholders
                + ")";

        Object[] bindValues = values.toArray();
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < bindValues.length; i++) {
                statement.setObject(i + 1, bindValues[i]);
            }
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }

        return queryLatestCreatedTaskId(videoId, sourceFilePath, videoIdColumn, sourceFilePathColumn);
    }

    private String buildPlaceholders(int totalColumnCount, boolean hasCreatedAt, boolean hasUpdatedAt) {
        int rawCount = (hasCreatedAt ? 1 : 0) + (hasUpdatedAt ? 1 : 0);
        int valueCount = totalColumnCount - rawCount;

        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < valueCount; i++) {
            placeholders.add("?");
        }
        if (hasCreatedAt) {
            placeholders.add("NOW()");
        }
        if (hasUpdatedAt) {
            placeholders.add("NOW()");
        }

        return String.join(",", placeholders);
    }

    private void add(List<String> columns, List<Object> values, String column, Object value) {
        columns.add(quote(column));
        values.add(value);
    }

    private void addRaw(List<String> columns, String column) {
        columns.add(quote(column));
    }

    private Long queryLatestCreatedTaskId(
            Long videoId,
            String sourceFilePath,
            String videoIdColumn,
            String sourceFilePathColumn
    ) {
        List<Long> rows = jdbcTemplate.query(
                "SELECT id FROM video_transcode_task WHERE "
                        + quote(videoIdColumn)
                        + " = ? AND "
                        + quote(sourceFilePathColumn)
                        + " = ? ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                videoId,
                sourceFilePath
        );

        if (rows.isEmpty()) {
            throw new BizException("HLS 修复执行失败：补建转码任务后未能读取 ID");
        }

        return rows.get(0);
    }

    private String resolveSourceFilePath(Long videoId, RepairTaskSnapshot repairTask) {
        List<String> candidates = new ArrayList<>();

        candidates.addAll(findPathCandidatesFromTable("video_upload_record", videoId));
        candidates.addAll(findPathCandidatesFromTable("video", videoId));
        candidates.addAll(findPathCandidatesFromTable("video_file", videoId));

        candidates.addAll(buildStoragePathCandidates(videoId));

        for (String candidate : candidates) {
            String normalized = normalizeLocalPath(candidate);
            if (normalized == null || normalized.isBlank()) {
                continue;
            }

            try {
                Path path = Path.of(normalized);
                if (Files.exists(path) && Files.isRegularFile(path)) {
                    return path.toAbsolutePath().normalize().toString();
                }
            } catch (Exception ignored) {
                // 忽略非法路径，继续尝试下一个候选
            }
        }

        return null;
    }

    private List<String> findPathCandidatesFromTable(String tableName, Long videoId) {
        if (!tableExists(tableName)) {
            return List.of();
        }

        TableColumns columns = columns(tableName);
        String videoIdColumn = "video".equals(tableName)
                ? columns.firstExisting("id")
                : columns.firstExisting("video_id", "videoId", "id");

        if (videoIdColumn == null) {
            return List.of();
        }

        List<String> pathColumns = columns.existing(
                "source_file_path",
                "sourceFilePath",
                "merged_file_path",
                "mergedFilePath",
                "merged_path",
                "mergedPath",
                "file_path",
                "filePath",
                "local_path",
                "localPath",
                "origin_path",
                "originPath",
                "source_path",
                "sourcePath",
                "temp_path",
                "tempPath",
                "path",
                "url"
        );

        if (pathColumns.isEmpty()) {
            return List.of();
        }

        String select = String.join(",", pathColumns.stream().map(this::quote).toList());
        String sql = "SELECT " + select + " FROM " + quote(tableName)
                + " WHERE " + quote(videoIdColumn) + " = ? ORDER BY "
                + quote(videoIdColumn) + " DESC LIMIT 20";

        List<String> result = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            for (String column : pathColumns) {
                String value = rs.getString(column);
                if (value != null && !value.isBlank()) {
                    result.add(value);
                }
            }
        }, videoId);

        return result;
    }

    private List<String> buildStoragePathCandidates(Long videoId) {
        List<String> result = new ArrayList<>();

        addStorageCandidates(result, storageProperties.getMergedRoot(), videoId);
        addStorageCandidates(result, storageProperties.getTempRoot(), videoId);

        return result;
    }

    private void addStorageCandidates(List<String> result, String root, Long videoId) {
        if (root == null || root.isBlank() || videoId == null) {
            return;
        }

        String id = String.valueOf(videoId);
        String[] names = {
                id + ".mp4",
                id + ".mov",
                id + ".mkv",
                id + ".avi",
                "video-" + id + ".mp4",
                "source-" + id + ".mp4",
                "origin-" + id + ".mp4"
        };

        for (String name : names) {
            result.add(Path.of(root, name).toString());
            result.add(Path.of(root, id, name).toString());
            result.add(Path.of(root, "videos", id, name).toString());
        }
    }

    private String normalizeLocalPath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String text = value.trim();

        if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("s3://") || text.startsWith("minio://")) {
            return null;
        }

        if (text.startsWith("file:")) {
            return text.substring("file:".length());
        }

        return text.replace("\\", "/");
    }

    private RepairTaskSnapshot loadRepairTask(Long repairTaskId) {
        List<RepairTaskSnapshot> rows = jdbcTemplate.query("""
                SELECT id, video_id, record_id, trace_id, manifest_object_key, status
                FROM video_hls_repair_task
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> new RepairTaskSnapshot(
                rs.getLong("id"),
                nullableLong(rs.getObject("video_id")),
                nullableLong(rs.getObject("record_id")),
                nullableLong(rs.getObject("trace_id")),
                rs.getString("manifest_object_key"),
                rs.getString("status")
        ), repairTaskId);

        if (rows.isEmpty()) {
            throw new BizException("HLS 修复执行失败：repair task 不存在，taskId=" + repairTaskId);
        }

        return rows.get(0);
    }

    private VideoTranscodeTask findLatestTranscodeTask(Long videoId) {
        LambdaQueryWrapper<VideoTranscodeTask> wrapper = new LambdaQueryWrapper<VideoTranscodeTask>()
                .eq(VideoTranscodeTask::getVideoId, videoId)
                .orderByDesc(VideoTranscodeTask::getId)
                .last("LIMIT 1");

        return transcodeTaskMapper.selectOne(wrapper);
    }

    private void resetTranscodeTask(VideoTranscodeTask transcodeTask) {
        transcodeTask.setStatus("PENDING");
        transcodeTask.setErrorMessage(null);
        transcodeTaskMapper.updateById(transcodeTask);
    }

    private boolean tableExists(String tableName) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, Long.class, tableName);
        return count != null && count > 0;
    }

    private TableColumns columns(String tableName) {
        List<String> values = jdbcTemplate.query("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, (rs, rowNum) -> rs.getString("column_name"), tableName);

        return new TableColumns(values);
    }

    private String quote(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]+")) {
            throw new BizException("非法 SQL 名称：" + name);
        }
        return "`" + name + "`";
    }

    private Long readLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record RepairTaskSnapshot(
            Long id,
            Long videoId,
            Long recordId,
            Long traceId,
            String manifestObjectKey,
            String status
    ) {
    }

    private static final class TableColumns {
        private final Map<String, String> actualByLower;

        private TableColumns(List<String> columns) {
            this.actualByLower = new LinkedHashMap<>();
            for (String column : columns) {
                this.actualByLower.put(column.toLowerCase(Locale.ROOT), column);
            }
        }

        String firstExisting(String... candidates) {
            for (String candidate : candidates) {
                String actual = actualByLower.get(candidate.toLowerCase(Locale.ROOT));
                if (actual != null) {
                    return actual;
                }
            }
            return null;
        }

        List<String> existing(String... candidates) {
            List<String> result = new ArrayList<>();
            for (String candidate : candidates) {
                String actual = actualByLower.get(candidate.toLowerCase(Locale.ROOT));
                if (actual != null && !result.contains(actual)) {
                    result.add(actual);
                }
            }
            return result;
        }
    }
}
