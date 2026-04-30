package com.vspicy.video.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.config.MinioProperties;
import com.vspicy.video.dto.VideoStorageCleanupCommand;
import com.vspicy.video.dto.VideoStorageCleanupResult;
import com.vspicy.video.dto.VideoStorageScanItem;
import com.vspicy.video.dto.VideoStorageScanResult;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.messages.Item;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class VideoStorageScanService {
    private final JdbcTemplate jdbcTemplate;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public VideoStorageScanService(
            JdbcTemplate jdbcTemplate,
            MinioClient minioClient,
            MinioProperties minioProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    public VideoStorageScanResult scan(String prefix, Integer limit) {
        String bucket = bucket();
        String safePrefix = normalizePrefix(prefix);
        int safeLimit = normalizeLimit(limit);

        List<DbObjectRef> dbObjects = loadDbObjects(safePrefix, safeLimit);
        MinioLoadResult minioLoad = loadMinioObjects(bucket, safePrefix, safeLimit);
        Map<String, MinioObjectRef> minioObjects = minioLoad.objects();
        boolean minioAvailable = minioLoad.errorMessage() == null;

        List<VideoStorageScanItem> items = new ArrayList<>();
        if (!minioAvailable) {
            items.add(new VideoStorageScanItem(
                    "MINIO_SCAN_FAILED",
                    bucket,
                    safePrefix,
                    null,
                    null,
                    null,
                    null,
                    "MINIO",
                    "扫描 MinIO 对象失败：" + minioLoad.errorMessage()
            ));
        } else {
            addDbMissingObjectItems(bucket, dbObjects, items);
            addObjectMissingDbItems(bucket, minioObjects, dbObjects, items);
        }

        long dbMissingObjectCount = items.stream()
                .filter(item -> "DB_MISSING_OBJECT".equals(item.issueType()))
                .count();

        long objectMissingDbCount = items.stream()
                .filter(item -> "OBJECT_MISSING_DB".equals(item.issueType()))
                .count();

        return new VideoStorageScanResult(
                bucket,
                safePrefix,
                safeLimit,
                (long) dbObjects.size(),
                (long) minioObjects.size(),
                dbMissingObjectCount,
                objectMissingDbCount,
                items
        );
    }

    public VideoStorageCleanupResult cleanup(VideoStorageCleanupCommand command) {
        String prefix = command == null ? null : command.prefix();
        Integer limit = command == null ? null : command.limit();
        boolean dryRun = command == null || command.dryRun() == null || Boolean.TRUE.equals(command.dryRun());

        VideoStorageScanResult scan = scan(prefix, limit);
        boolean minioScanFailed = scan.items().stream()
                .anyMatch(item -> "MINIO_SCAN_FAILED".equals(item.issueType()));
        if (minioScanFailed) {
            return new VideoStorageCleanupResult(
                    scan.bucket(),
                    scan.prefix(),
                    true,
                    0L,
                    0L,
                    List.of(),
                    "MinIO 扫描失败，已跳过对象清理。请检查 endpoint、access-key、secret-key 和 bucket 配置。"
            );
        }

        List<VideoStorageScanItem> candidates = scan.items().stream()
                .filter(item -> "OBJECT_MISSING_DB".equals(item.issueType()))
                .toList();

        long deletedCount = 0L;
        if (!dryRun) {
            for (VideoStorageScanItem candidate : candidates) {
                removeObject(scan.bucket(), candidate.objectKey());
                deletedCount++;
            }
        }

        return new VideoStorageCleanupResult(
                scan.bucket(),
                scan.prefix(),
                dryRun,
                (long) candidates.size(),
                deletedCount,
                candidates,
                dryRun ? "dryRun=true，仅预览未删除" : "孤儿对象清理完成"
        );
    }

    private void addDbMissingObjectItems(String bucket, List<DbObjectRef> dbObjects, List<VideoStorageScanItem> items) {
        for (DbObjectRef dbObject : dbObjects) {
            if (!objectExists(bucket, dbObject.objectKey())) {
                items.add(new VideoStorageScanItem(
                        "DB_MISSING_OBJECT",
                        bucket,
                        dbObject.objectKey(),
                        null,
                        dbObject.recordId(),
                        dbObject.traceTableId(),
                        dbObject.videoId(),
                        dbObject.source(),
                        "数据库存在 objectKey，但 MinIO 对象不存在"
                ));
            }
        }
    }

    private void addObjectMissingDbItems(
            String bucket,
            Map<String, MinioObjectRef> minioObjects,
            List<DbObjectRef> dbObjects,
            List<VideoStorageScanItem> items
    ) {
        Set<String> dbKeys = new HashSet<>();
        for (DbObjectRef dbObject : dbObjects) {
            dbKeys.add(dbObject.objectKey());
        }

        for (MinioObjectRef minioObject : minioObjects.values()) {
            if (!dbKeys.contains(minioObject.objectKey())) {
                items.add(new VideoStorageScanItem(
                        "OBJECT_MISSING_DB",
                        bucket,
                        minioObject.objectKey(),
                        minioObject.size(),
                        null,
                        null,
                        null,
                        "MINIO",
                        "MinIO 对象存在，但数据库没有追踪记录"
                ));
            }
        }
    }

    private List<DbObjectRef> loadDbObjects(String prefix, int limit) {
        List<DbObjectRef> result = new ArrayList<>();

        result.addAll(jdbcTemplate.query("""
                SELECT id AS record_id, NULL AS trace_table_id, video_id, object_key, 'video_upload_record' AS source
                FROM video_upload_record
                WHERE object_key IS NOT NULL
                  AND object_key <> ''
                  AND object_key LIKE ?
                ORDER BY id DESC
                LIMIT ?
                """, (rs, rowNum) -> new DbObjectRef(
                nullableLong(rs.getObject("record_id")),
                nullableLong(rs.getObject("trace_table_id")),
                nullableLong(rs.getObject("video_id")),
                rs.getString("object_key"),
                rs.getString("source")
        ), prefix + "%", limit));

        result.addAll(jdbcTemplate.query("""
                SELECT record_id, id AS trace_table_id, video_id, object_key, 'video_upload_trace' AS source
                FROM video_upload_trace
                WHERE object_key IS NOT NULL
                  AND object_key <> ''
                  AND object_key LIKE ?
                ORDER BY id DESC
                LIMIT ?
                """, (rs, rowNum) -> new DbObjectRef(
                nullableLong(rs.getObject("record_id")),
                nullableLong(rs.getObject("trace_table_id")),
                nullableLong(rs.getObject("video_id")),
                rs.getString("object_key"),
                rs.getString("source")
        ), prefix + "%", limit));

        Map<String, DbObjectRef> dedup = new LinkedHashMap<>();
        for (DbObjectRef item : result) {
            dedup.putIfAbsent(item.objectKey(), item);
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

    private boolean objectExists(String bucket, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }

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

    private void removeObject(String bucket, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception ex) {
            throw new BizException("删除 MinIO 对象失败：" + objectKey + "，" + ex.getMessage());
        }
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

    private record DbObjectRef(
            Long recordId,
            Long traceTableId,
            Long videoId,
            String objectKey,
            String source
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
}
