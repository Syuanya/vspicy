package com.vspicy.video.service;

import com.vspicy.video.dto.VideoPlaybackReadinessSyncCommand;
import com.vspicy.video.dto.VideoPlaybackReadinessSyncResult;
import com.vspicy.video.dto.VideoPlaybackReadinessView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class VideoPlaybackReadinessService {
    private final JdbcTemplate jdbcTemplate;

    public VideoPlaybackReadinessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public VideoPlaybackReadinessView readiness(Long videoId) {
        if (videoId == null) {
            return new VideoPlaybackReadinessView(
                    null,
                    false,
                    null,
                    false,
                    null,
                    false,
                    false,
                    null,
                    false,
                    "videoId 不能为空",
                    "CHECK_VIDEO_ID",
                    List.of()
            );
        }

        VideoSnapshot video = loadVideo(videoId);
        HlsManifest hls = findHlsManifest(videoId);
        List<String> playbackColumns = playbackColumns();
        String playbackUrl = video == null ? null : firstPlaybackUrl(videoId, playbackColumns);

        boolean videoExists = video != null;
        boolean statusPublished = video != null && isPublished(video.status());
        boolean playbackUrlPresent = playbackUrl != null && !playbackUrl.isBlank();
        boolean playable = hls.ready() && statusPublished && playbackUrlPresent;

        return new VideoPlaybackReadinessView(
                videoId,
                videoExists,
                video == null ? null : video.status(),
                hls.ready(),
                hls.manifestKey(),
                statusPublished,
                playbackUrlPresent,
                playbackUrl,
                playable,
                message(videoExists, hls.ready(), statusPublished, playbackUrlPresent),
                suggestedAction(videoExists, hls.ready(), statusPublished, playbackUrlPresent),
                playbackColumns
        );
    }

    @Transactional
    public VideoPlaybackReadinessSyncResult sync(Long videoId, VideoPlaybackReadinessSyncCommand command) {
        boolean dryRun = command == null || command.dryRun() == null || Boolean.TRUE.equals(command.dryRun());
        String reason = command == null || command.reason() == null ? "manual playback readiness sync" : command.reason();

        VideoSnapshot video = loadVideo(videoId);
        HlsManifest hls = findHlsManifest(videoId);

        if (video == null) {
            VideoPlaybackReadinessSyncResult result = new VideoPlaybackReadinessSyncResult(
                    videoId,
                    dryRun,
                    false,
                    hls.ready(),
                    hls.manifestKey(),
                    null,
                    null,
                    List.of(),
                    List.of(),
                    "视频不存在，无法同步",
                    "video not found"
            );
            log(result, reason);
            return result;
        }

        if (!hls.ready()) {
            VideoPlaybackReadinessSyncResult result = new VideoPlaybackReadinessSyncResult(
                    videoId,
                    dryRun,
                    false,
                    false,
                    null,
                    video.status(),
                    video.status(),
                    List.of(),
                    List.of(),
                    "未检测到 HLS manifest，无法同步",
                    "hls manifest not found"
            );
            log(result, reason);
            return result;
        }

        List<String> columnsToUpdate = columnsToUpdate();
        if (columnsToUpdate.isEmpty()) {
            VideoPlaybackReadinessSyncResult result = new VideoPlaybackReadinessSyncResult(
                    videoId,
                    dryRun,
                    false,
                    true,
                    hls.manifestKey(),
                    video.status(),
                    video.status(),
                    List.of(),
                    List.of(),
                    "video 表没有可同步字段",
                    "no sync columns"
            );
            log(result, reason);
            return result;
        }

        if (dryRun) {
            VideoPlaybackReadinessSyncResult result = new VideoPlaybackReadinessSyncResult(
                    videoId,
                    true,
                    true,
                    true,
                    hls.manifestKey(),
                    video.status(),
                    "PUBLISHED",
                    columnsToUpdate,
                    List.of(),
                    "dryRun 预览成功，未修改数据",
                    null
            );
            log(result, reason);
            return result;
        }

        List<String> updatedColumns = executeUpdate(videoId, hls.manifestKey(), columnsToUpdate);
        VideoPlaybackReadinessSyncResult result = new VideoPlaybackReadinessSyncResult(
                videoId,
                false,
                true,
                true,
                hls.manifestKey(),
                video.status(),
                "PUBLISHED",
                columnsToUpdate,
                updatedColumns,
                "播放就绪信息已同步",
                null
        );
        log(result, reason);
        return result;
    }

    private List<String> executeUpdate(Long videoId, String manifestKey, List<String> columnsToUpdate) {
        List<String> assignments = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        List<String> updated = new ArrayList<>();

        for (String column : columnsToUpdate) {
            if ("status".equals(column)) {
                assignments.add("`status` = ?");
                args.add("PUBLISHED");
                updated.add(column);
            } else if ("updated_at".equals(column) || "updatedAt".equals(column)) {
                assignments.add("`" + column + "` = NOW()");
                updated.add(column);
            } else {
                assignments.add("`" + column + "` = ?");
                args.add(manifestKey);
                updated.add(column);
            }
        }

        args.add(videoId);

        String sql = "UPDATE video SET " + String.join(", ", assignments) + " WHERE id = ?";
        jdbcTemplate.update(sql, args.toArray());

        return updated;
    }

    private List<String> columnsToUpdate() {
        List<String> result = new ArrayList<>();

        if (columnExists("video", "status")) {
            result.add("status");
        }

        for (String column : List.of("local_hls_url", "localHlsUrl", "minio_hls_url", "minioHlsUrl", "hls_url", "hlsUrl", "play_url", "playUrl")) {
            if (columnExists("video", column)) {
                result.add(column);
            }
        }

        if (columnExists("video", "updated_at")) {
            result.add("updated_at");
        } else if (columnExists("video", "updatedAt")) {
            result.add("updatedAt");
        }

        return result;
    }

    private List<String> playbackColumns() {
        List<String> result = new ArrayList<>();
        for (String column : List.of("local_hls_url", "localHlsUrl", "minio_hls_url", "minioHlsUrl", "hls_url", "hlsUrl", "play_url", "playUrl")) {
            if (columnExists("video", column)) {
                result.add(column);
            }
        }
        return result;
    }

    private String firstPlaybackUrl(Long videoId, List<String> columns) {
        for (String column : columns) {
            List<String> rows = jdbcTemplate.query(
                    "SELECT `" + column + "` FROM video WHERE id = ? LIMIT 1",
                    (rs, rowNum) -> rs.getString(column),
                    videoId
            );
            if (!rows.isEmpty() && rows.get(0) != null && !rows.get(0).isBlank()) {
                return rows.get(0);
            }
        }
        return null;
    }

    private VideoSnapshot loadVideo(Long videoId) {
        if (!tableExists("video")) {
            return null;
        }

        String statusColumn = columnExists("video", "status") ? "status" : null;
        String select = statusColumn == null ? "id" : "id, `" + statusColumn + "` AS status";

        List<VideoSnapshot> rows = jdbcTemplate.query(
                "SELECT " + select + " FROM video WHERE id = ? LIMIT 1",
                (rs, rowNum) -> new VideoSnapshot(
                        rs.getLong("id"),
                        statusColumn == null ? null : rs.getString("status")
                ),
                videoId
        );

        return rows.isEmpty() ? null : rows.get(0);
    }

    private HlsManifest findHlsManifest(Long videoId) {
        if (videoId == null || !tableExists("video_file") || !columnExists("video_file", "video_id")) {
            return HlsManifest.notReady();
        }

        for (String column : List.of("object_key", "file_path", "url")) {
            if (!columnExists("video_file", column)) {
                continue;
            }

            List<String> rows = jdbcTemplate.query(
                    "SELECT `" + column + "` FROM video_file WHERE video_id = ? AND `" + column + "` LIKE '%.m3u8%' ORDER BY id DESC LIMIT 1",
                    (rs, rowNum) -> rs.getString(column),
                    videoId
            );

            if (!rows.isEmpty() && rows.get(0) != null && !rows.get(0).isBlank()) {
                return new HlsManifest(true, rows.get(0));
            }
        }

        if (columnExists("video_file", "file_type")) {
            String selectColumn = firstExistingColumn("video_file", "object_key", "file_path", "url");
            if (selectColumn != null) {
                List<String> rows = jdbcTemplate.query(
                        "SELECT `" + selectColumn + "` FROM video_file WHERE video_id = ? AND file_type IN ('HLS','M3U8','HLS_INDEX','MASTER_PLAYLIST') ORDER BY id DESC LIMIT 1",
                        (rs, rowNum) -> rs.getString(selectColumn),
                        videoId
                );
                if (!rows.isEmpty() && rows.get(0) != null && !rows.get(0).isBlank()) {
                    return new HlsManifest(true, rows.get(0));
                }
            }
        }

        return HlsManifest.notReady();
    }

    private String message(boolean videoExists, boolean hlsReady, boolean statusPublished, boolean playbackUrlPresent) {
        if (!videoExists) {
            return "视频不存在。";
        }
        if (!hlsReady) {
            return "未检测到 HLS manifest。";
        }
        if (!statusPublished || !playbackUrlPresent) {
            return "HLS 已就绪，但 video 状态或播放地址未同步。";
        }
        return "视频播放就绪。";
    }

    private String suggestedAction(boolean videoExists, boolean hlsReady, boolean statusPublished, boolean playbackUrlPresent) {
        if (!videoExists) {
            return "CHECK_VIDEO";
        }
        if (!hlsReady) {
            return "WAIT_OR_RERUN";
        }
        if (!statusPublished || !playbackUrlPresent) {
            return "SYNC";
        }
        return "PLAY";
    }

    private boolean isPublished(String status) {
        return "PUBLISHED".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status) || "READY".equalsIgnoreCase(status);
    }

    private String firstExistingColumn(String table, String... columns) {
        for (String column : columns) {
            if (columnExists(table, column)) {
                return column;
            }
        }
        return null;
    }

    private boolean tableExists(String tableName) {
        try {
            Long value = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                    """, Long.class, tableName);
            return value != null && value > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        try {
            Long value = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                      AND column_name = ?
                    """, Long.class, tableName, columnName);
            return value != null && value > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private void log(VideoPlaybackReadinessSyncResult result, String reason) {
        try {
            if (!tableExists("video_playback_readiness_sync_log")) {
                return;
            }

            jdbcTemplate.update("""
                    INSERT INTO video_playback_readiness_sync_log(
                      video_id, dry_run, hls_ready, hls_manifest_key,
                      video_status_before, video_status_after,
                      updated_columns, success, reason, error_message
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    result.videoId(),
                    Boolean.TRUE.equals(result.dryRun()) ? 1 : 0,
                    Boolean.TRUE.equals(result.hlsReady()) ? 1 : 0,
                    result.hlsManifestKey(),
                    result.videoStatusBefore(),
                    result.videoStatusAfter(),
                    result.columnsUpdated() == null ? null : String.join(",", result.columnsUpdated()),
                    Boolean.TRUE.equals(result.success()) ? 1 : 0,
                    reason,
                    result.errorMessage()
            );
        } catch (Exception ignored) {
            // 审计日志不能影响主流程
        }
    }

    private record VideoSnapshot(Long id, String status) {
    }

    private record HlsManifest(Boolean ready, String manifestKey) {
        static HlsManifest notReady() {
            return new HlsManifest(false, null);
        }
    }
}
