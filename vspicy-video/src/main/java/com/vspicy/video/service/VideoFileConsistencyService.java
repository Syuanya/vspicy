package com.vspicy.video.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.config.MinioProperties;
import com.vspicy.video.dto.VideoFileConsistencyItem;
import com.vspicy.video.dto.VideoFileConsistencyResult;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class VideoFileConsistencyService {
    private final JdbcTemplate jdbcTemplate;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public VideoFileConsistencyService(
            JdbcTemplate jdbcTemplate,
            MinioClient minioClient,
            MinioProperties minioProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    public VideoFileConsistencyResult check(String prefix, Integer limit) {
        String bucket = bucket();
        String safePrefix = normalizePrefix(prefix);
        int safeLimit = normalizeLimit(limit);

        boolean videoFileTableExists = tableExists("video_file");
        TableColumns videoFileColumns = videoFileTableExists ? columns("video_file") : TableColumns.empty();

        String videoFileObjectColumn = videoFileColumns.firstExisting(
                "object_key",
                "hls_object_key",
                "file_object_key",
                "storage_key",
                "minio_object_key",
                "path",
                "file_path",
                "file_url",
                "url",
                "hls_url"
        );

        List<VideoFileObjectRef> videoFileObjects = videoFileTableExists && videoFileObjectColumn != null
                ? loadVideoFileObjects(videoFileColumns, videoFileObjectColumn, safePrefix, safeLimit)
                : List.of();

        List<TraceObjectRef> traceObjects = loadTraceObjects(safePrefix, safeLimit);
        MinioLoadResult minioLoad = loadMinioObjects(bucket, safePrefix, safeLimit);
        Map<String, MinioObjectRef> minioObjects = minioLoad.objects();
        boolean minioAvailable = minioLoad.errorMessage() == null;

        Map<String, VideoFileObjectRef> videoFileByKey = new LinkedHashMap<>();
        for (VideoFileObjectRef item : videoFileObjects) {
            videoFileByKey.putIfAbsent(item.objectKey(), item);
        }

        Map<String, TraceObjectRef> traceByKey = new LinkedHashMap<>();
        for (TraceObjectRef item : traceObjects) {
            traceByKey.putIfAbsent(item.objectKey(), item);
        }

        List<VideoFileConsistencyItem> items = new ArrayList<>();
        if (!minioAvailable) {
            items.add(new VideoFileConsistencyItem(
                    "MINIO_SCAN_FAILED",
                    bucket,
                    safePrefix,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "MINIO",
                    "扫描 MinIO 对象失败：" + minioLoad.errorMessage()
            ));
        }

        for (VideoFileObjectRef vf : videoFileObjects) {
            if (minioAvailable && !minioObjects.containsKey(vf.objectKey())) {
                items.add(new VideoFileConsistencyItem(
                        "VIDEO_FILE_MISSING_OBJECT",
                        bucket,
                        vf.objectKey(),
                        null,
                        vf.videoFileId(),
                        vf.videoId(),
                        null,
                        null,
                        "video_file",
                        "video_file 有记录，但 MinIO 对象不存在"
                ));
            }

            if (!traceByKey.containsKey(vf.objectKey())) {
                items.add(new VideoFileConsistencyItem(
                        "VIDEO_FILE_MISSING_TRACE",
                        bucket,
                        vf.objectKey(),
                        vf.size(),
                        vf.videoFileId(),
                        vf.videoId(),
                        null,
                        null,
                        "video_file",
                        "video_file 有记录，但 upload_trace 没有追踪记录"
                ));
            }
        }

        for (TraceObjectRef trace : traceObjects) {
            if (!videoFileByKey.containsKey(trace.objectKey())) {
                items.add(new VideoFileConsistencyItem(
                        "TRACE_MISSING_VIDEO_FILE",
                        bucket,
                        trace.objectKey(),
                        trace.size(),
                        null,
                        trace.videoId(),
                        trace.traceTableId(),
                        trace.recordId(),
                        "video_upload_trace",
                        "upload_trace 有记录，但 video_file 没有对应记录"
                ));
            }
        }

        if (minioAvailable) {
            for (MinioObjectRef minioObject : minioObjects.values()) {
                if (!videoFileByKey.containsKey(minioObject.objectKey())) {
                    items.add(new VideoFileConsistencyItem(
                            "OBJECT_MISSING_VIDEO_FILE",
                            bucket,
                            minioObject.objectKey(),
                            minioObject.size(),
                            null,
                            null,
                            null,
                            null,
                            "MINIO",
                            "MinIO 有对象，但 video_file 没有记录"
                    ));
                }
            }
        }

        return new VideoFileConsistencyResult(
                bucket,
                safePrefix,
                safeLimit,
                videoFileTableExists,
                videoFileObjectColumn,
                (long) videoFileObjects.size(),
                (long) traceObjects.size(),
                (long) minioObjects.size(),
                count(items, "VIDEO_FILE_MISSING_OBJECT"),
                count(items, "VIDEO_FILE_MISSING_TRACE"),
                count(items, "TRACE_MISSING_VIDEO_FILE"),
                count(items, "OBJECT_MISSING_VIDEO_FILE"),
                items
        );
    }

    private List<VideoFileObjectRef> loadVideoFileObjects(
            TableColumns columns,
            String objectColumn,
            String prefix,
            int limit
    ) {
        String idColumn = columns.firstExisting("id");
        String videoIdColumn = columns.firstExisting("video_id", "videoId", "media_id", "biz_id");
        String sizeColumn = columns.firstExisting("size_bytes", "file_size", "size", "size_mb");

        String selectId = idColumn == null ? "NULL" : quote(idColumn);
        String selectVideoId = videoIdColumn == null ? "NULL" : quote(videoIdColumn);
        String selectSize = sizeColumn == null ? "NULL" : quote(sizeColumn);

        String sql = """
                SELECT %s AS video_file_id,
                       %s AS video_id,
                       %s AS object_key_raw,
                       %s AS object_size
                FROM video_file
                WHERE %s IS NOT NULL
                  AND %s <> ''
                ORDER BY %s DESC
                LIMIT ?
                """.formatted(
                selectId,
                selectVideoId,
                quote(objectColumn),
                selectSize,
                quote(objectColumn),
                quote(objectColumn),
                selectId.equals("NULL") ? quote(objectColumn) : selectId
        );

        List<VideoFileObjectRef> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            String objectKey = normalizeObjectKey(rs.getString("object_key_raw"), prefix);
            return new VideoFileObjectRef(
                    nullableLong(rs.getObject("video_file_id")),
                    nullableLong(rs.getObject("video_id")),
                    objectKey,
                    nullableLong(rs.getObject("object_size"))
            );
        }, limit);

        Map<String, VideoFileObjectRef> dedup = new LinkedHashMap<>();
        for (VideoFileObjectRef item : rows) {
            if (item.objectKey() != null && item.objectKey().startsWith(prefix)) {
                dedup.putIfAbsent(item.objectKey(), item);
            }
        }

        return new ArrayList<>(dedup.values());
    }

    private List<TraceObjectRef> loadTraceObjects(String prefix, int limit) {
        if (!tableExists("video_upload_trace") || !columnExists("video_upload_trace", "object_key")) {
            return List.of();
        }

        List<TraceObjectRef> rows = jdbcTemplate.query("""
                SELECT id, record_id, video_id, object_key, size_mb
                FROM video_upload_trace
                WHERE object_key IS NOT NULL
                  AND object_key <> ''
                ORDER BY id DESC
                LIMIT ?
                """, (rs, rowNum) -> {
            String objectKey = normalizeObjectKey(rs.getString("object_key"), prefix);
            Long sizeMb = nullableLong(rs.getObject("size_mb"));
            Long sizeBytes = sizeMb == null ? null : sizeMb * 1024L * 1024L;
            return new TraceObjectRef(
                    rs.getLong("id"),
                    nullableLong(rs.getObject("record_id")),
                    nullableLong(rs.getObject("video_id")),
                    objectKey,
                    sizeBytes
            );
        }, limit);

        Map<String, TraceObjectRef> dedup = new LinkedHashMap<>();
        for (TraceObjectRef item : rows) {
            if (item.objectKey() != null && item.objectKey().startsWith(prefix)) {
                dedup.putIfAbsent(item.objectKey(), item);
            }
        }

        return new ArrayList<>(dedup.values());
    }

    private MinioLoadResult loadMinioObjects(String bucket, String prefix, int limit) {
        Map<String, MinioObjectRef> result = new LinkedHashMap<>();

        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );

            int count = 0;
            for (Result<Item> objectResult : objects) {
                if (count >= limit) {
                    break;
                }

                Item item = objectResult.get();
                if (item.isDir()) {
                    continue;
                }

                result.put(item.objectName(), new MinioObjectRef(item.objectName(), item.size()));
                count++;
            }

            return new MinioLoadResult(result, null);
        } catch (Exception ex) {
            return new MinioLoadResult(result, ex.getMessage());
        }
    }

    private String normalizeObjectKey(String value, String prefix) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String text = value.trim().replace("\\", "/");
        String bucket = bucket();

        int bucketIndex = text.indexOf("/" + bucket + "/");
        if (bucketIndex >= 0) {
            return text.substring(bucketIndex + bucket.length() + 2);
        }

        int prefixIndex = text.indexOf(prefix);
        if (prefixIndex >= 0) {
            return text.substring(prefixIndex);
        }

        return text;
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

    private boolean columnExists(String tableName, String columnName) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Long.class, tableName, columnName);
        return count != null && count > 0;
    }

    private TableColumns columns(String tableName) {
        List<String> columns = jdbcTemplate.query("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, (rs, rowNum) -> rs.getString("column_name"), tableName);

        return new TableColumns(columns);
    }

    private String quote(String column) {
        if (column == null || !column.matches("[A-Za-z0-9_]+")) {
            throw new BizException("非法字段名：" + column);
        }
        return "`" + column + "`";
    }

    private long count(List<VideoFileConsistencyItem> items, String issueType) {
        return items.stream().filter(item -> issueType.equals(item.issueType())).count();
    }

    private String bucket() {
        String bucket = minioProperties.getBucket();
        if (bucket == null || bucket.isBlank()) {
            return "vspicy";
        }
        return bucket;
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "videos/";
        }
        return prefix;
    }

    private int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 || limit > 10000 ? 1000 : limit;
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record VideoFileObjectRef(
            Long videoFileId,
            Long videoId,
            String objectKey,
            Long size
    ) {
    }

    private record TraceObjectRef(
            Long traceTableId,
            Long recordId,
            Long videoId,
            String objectKey,
            Long size
    ) {
    }

    private record MinioObjectRef(
            String objectKey,
            Long size
    ) {
    }

    private record MinioLoadResult(
            Map<String, MinioObjectRef> objects,
            String errorMessage
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

        static TableColumns empty() {
            return new TableColumns(List.of());
        }

        String firstExisting(String... candidates) {
            if (candidates == null) {
                return null;
            }

            for (String candidate : candidates) {
                String actual = actualByLower.get(candidate.toLowerCase(Locale.ROOT));
                if (actual != null) {
                    return actual;
                }
            }

            return null;
        }
    }
}
