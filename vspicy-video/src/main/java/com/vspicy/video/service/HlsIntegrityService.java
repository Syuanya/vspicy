package com.vspicy.video.service;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.config.MinioProperties;
import com.vspicy.video.dto.HlsIntegrityItem;
import com.vspicy.video.dto.HlsIntegrityResult;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class HlsIntegrityService {
    private final JdbcTemplate jdbcTemplate;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public HlsIntegrityService(
            JdbcTemplate jdbcTemplate,
            MinioClient minioClient,
            MinioProperties minioProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    public HlsIntegrityResult scan(String prefix, Integer limit) {
        String bucket = bucket();
        String safePrefix = normalizePrefix(prefix);
        int safeLimit = normalizeLimit(limit);

        List<ManifestRef> manifests = loadManifestRefs(safePrefix, safeLimit);
        List<HlsIntegrityItem> items = new ArrayList<>();

        for (ManifestRef manifest : manifests) {
            items.add(checkManifest(bucket, manifest));
        }

        return buildResult(bucket, safePrefix, safeLimit, items);
    }

    public HlsIntegrityResult checkObject(String objectKey) {
        String bucket = bucket();
        if (objectKey == null || objectKey.isBlank()) {
            throw new BizException("objectKey 不能为空");
        }

        String normalized = normalizeObjectKey(objectKey);
        HlsIntegrityItem item = checkManifest(bucket, new ManifestRef(
                normalized,
                null,
                null,
                null,
                "REQUEST"
        ));

        String prefix = parentPrefix(normalized);
        return buildResult(bucket, prefix, 1, List.of(item));
    }

    private HlsIntegrityResult buildResult(String bucket, String prefix, int limit, List<HlsIntegrityItem> items) {
        long okCount = count(items, "HLS_OK");
        long manifestMissingCount = count(items, "HLS_MANIFEST_MISSING");
        long manifestEmptyCount = count(items, "HLS_MANIFEST_EMPTY");
        long manifestReadFailedCount = count(items, "HLS_MANIFEST_READ_FAILED");
        long segmentMissingCount = count(items, "HLS_SEGMENT_MISSING");

        return new HlsIntegrityResult(
                bucket,
                prefix,
                limit,
                (long) items.size(),
                okCount,
                manifestMissingCount,
                manifestEmptyCount,
                manifestReadFailedCount,
                segmentMissingCount,
                items
        );
    }

    private HlsIntegrityItem checkManifest(String bucket, ManifestRef manifest) {
        String manifestObjectKey = normalizeObjectKey(manifest.objectKey());

        if (!objectExists(bucket, manifestObjectKey)) {
            return new HlsIntegrityItem(
                    "HLS_MANIFEST_MISSING",
                    bucket,
                    manifestObjectKey,
                    manifest.videoId(),
                    manifest.recordId(),
                    manifest.traceId(),
                    0,
                    0,
                    List.of(),
                    manifest.source(),
                    "index.m3u8 不存在"
            );
        }

        String content;
        try {
            content = readObject(bucket, manifestObjectKey);
        } catch (Exception ex) {
            return new HlsIntegrityItem(
                    "HLS_MANIFEST_READ_FAILED",
                    bucket,
                    manifestObjectKey,
                    manifest.videoId(),
                    manifest.recordId(),
                    manifest.traceId(),
                    0,
                    0,
                    List.of(),
                    manifest.source(),
                    "读取 m3u8 失败：" + ex.getMessage()
            );
        }

        if (content == null || content.isBlank()) {
            return new HlsIntegrityItem(
                    "HLS_MANIFEST_EMPTY",
                    bucket,
                    manifestObjectKey,
                    manifest.videoId(),
                    manifest.recordId(),
                    manifest.traceId(),
                    0,
                    0,
                    List.of(),
                    manifest.source(),
                    "m3u8 内容为空"
            );
        }

        List<String> segments = parseSegments(manifestObjectKey, content);
        List<String> missing = new ArrayList<>();

        for (String segment : segments) {
            if (!objectExists(bucket, segment)) {
                missing.add(segment);
            }
        }

        if (!missing.isEmpty()) {
            return new HlsIntegrityItem(
                    "HLS_SEGMENT_MISSING",
                    bucket,
                    manifestObjectKey,
                    manifest.videoId(),
                    manifest.recordId(),
                    manifest.traceId(),
                    segments.size(),
                    missing.size(),
                    missing,
                    manifest.source(),
                    "m3u8 引用的分片存在缺失"
            );
        }

        return new HlsIntegrityItem(
                "HLS_OK",
                bucket,
                manifestObjectKey,
                manifest.videoId(),
                manifest.recordId(),
                manifest.traceId(),
                segments.size(),
                0,
                List.of(),
                manifest.source(),
                "HLS 产物完整"
        );
    }

    private List<ManifestRef> loadManifestRefs(String prefix, int limit) {
        Map<String, ManifestRef> dedup = new LinkedHashMap<>();

        if (tableExists("video_upload_trace") && columnExists("video_upload_trace", "object_key")) {
            jdbcTemplate.query("""
                    SELECT id, record_id, video_id, object_key
                    FROM video_upload_trace
                    WHERE object_key IS NOT NULL
                      AND object_key <> ''
                      AND object_key LIKE ?
                      AND LOWER(object_key) LIKE '%%.m3u8'
                    ORDER BY id DESC
                    LIMIT ?
                    """, rs -> {
                String key = normalizeObjectKey(rs.getString("object_key"));
                dedup.putIfAbsent(key, new ManifestRef(
                        key,
                        nullableLong(rs.getObject("video_id")),
                        nullableLong(rs.getObject("record_id")),
                        nullableLong(rs.getObject("id")),
                        "video_upload_trace"
                ));
            }, prefix + "%", limit);
        }

        if (tableExists("video_upload_record") && columnExists("video_upload_record", "object_key")) {
            jdbcTemplate.query("""
                    SELECT id, video_id, object_key
                    FROM video_upload_record
                    WHERE object_key IS NOT NULL
                      AND object_key <> ''
                      AND object_key LIKE ?
                      AND LOWER(object_key) LIKE '%%.m3u8'
                    ORDER BY id DESC
                    LIMIT ?
                    """, rs -> {
                String key = normalizeObjectKey(rs.getString("object_key"));
                dedup.putIfAbsent(key, new ManifestRef(
                        key,
                        nullableLong(rs.getObject("video_id")),
                        nullableLong(rs.getObject("id")),
                        null,
                        "video_upload_record"
                ));
            }, prefix + "%", limit);
        }

        for (String minioManifest : listMinioM3u8(prefix, limit)) {
            dedup.putIfAbsent(minioManifest, new ManifestRef(
                    minioManifest,
                    null,
                    null,
                    null,
                    "MINIO"
            ));
            if (dedup.size() >= limit) {
                break;
            }
        }

        return new ArrayList<>(dedup.values());
    }

    private List<String> listMinioM3u8(String prefix, int limit) {
        List<String> result = new ArrayList<>();
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket())
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> objectResult : objects) {
                if (result.size() >= limit) {
                    break;
                }

                Item item = objectResult.get();
                if (!item.isDir() && item.objectName().toLowerCase(Locale.ROOT).endsWith(".m3u8")) {
                    result.add(item.objectName());
                }
            }
            return result;
        } catch (Exception ex) {
            throw new BizException("扫描 MinIO m3u8 失败：" + ex.getMessage());
        }
    }

    private List<String> parseSegments(String manifestObjectKey, String content) {
        String basePrefix = parentPrefix(manifestObjectKey);
        List<String> segments = new ArrayList<>();

        String[] lines = content.split("\\R");
        for (String line : lines) {
            String text = line == null ? "" : line.trim();
            if (text.isBlank() || text.startsWith("#")) {
                continue;
            }

            String lower = text.toLowerCase(Locale.ROOT);
            if (!(lower.endsWith(".ts")
                    || lower.endsWith(".m4s")
                    || lower.endsWith(".mp4")
                    || lower.contains(".ts?")
                    || lower.contains(".m4s?")
                    || lower.contains(".mp4?"))) {
                continue;
            }

            String cleaned = stripQuery(text);
            if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                cleaned = normalizeObjectKey(cleaned);
            } else if (!cleaned.startsWith(basePrefix)) {
                cleaned = basePrefix + cleaned;
            }

            segments.add(cleaned);
        }

        return segments;
    }

    private String stripQuery(String value) {
        int q = value.indexOf('?');
        if (q >= 0) {
            return value.substring(0, q);
        }
        return value;
    }

    private String readObject(String bucket, String objectKey) throws Exception {
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build()
        );
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
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

    private String normalizeObjectKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String text = value.trim().replace("\\", "/");
        String bucket = bucket();

        int bucketIndex = text.indexOf("/" + bucket + "/");
        if (bucketIndex >= 0) {
            return text.substring(bucketIndex + bucket.length() + 2);
        }

        int videosIndex = text.indexOf("videos/");
        if (videosIndex >= 0) {
            return text.substring(videosIndex);
        }

        return text;
    }

    private String parentPrefix(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return "videos/";
        }
        int idx = objectKey.lastIndexOf('/');
        if (idx < 0) {
            return "";
        }
        return objectKey.substring(0, idx + 1);
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

    private long count(List<HlsIntegrityItem> items, String status) {
        return items.stream().filter(item -> status.equals(item.status())).count();
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
        return limit == null || limit <= 0 || limit > 5000 ? 200 : limit;
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record ManifestRef(
            String objectKey,
            Long videoId,
            Long recordId,
            Long traceId,
            String source
    ) {
    }
}
