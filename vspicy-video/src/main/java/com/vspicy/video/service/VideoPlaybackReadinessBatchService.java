package com.vspicy.video.service;

import com.vspicy.video.dto.VideoPlaybackReadinessBatchCommand;
import com.vspicy.video.dto.VideoPlaybackReadinessBatchResult;
import com.vspicy.video.dto.VideoPlaybackReadinessSyncCommand;
import com.vspicy.video.dto.VideoPlaybackReadinessSyncResult;
import com.vspicy.video.dto.VideoPlaybackReadinessView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VideoPlaybackReadinessBatchService {
    private final JdbcTemplate jdbcTemplate;
    private final VideoPlaybackReadinessService readinessService;

    public VideoPlaybackReadinessBatchService(
            JdbcTemplate jdbcTemplate,
            VideoPlaybackReadinessService readinessService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.readinessService = readinessService;
    }

    public VideoPlaybackReadinessBatchResult scan(Integer limit, Boolean onlyProblem) {
        int safeLimit = normalizeLimit(limit);
        boolean problemOnly = onlyProblem == null || Boolean.TRUE.equals(onlyProblem);

        List<Long> videoIds = loadVideoIds(safeLimit);
        List<VideoPlaybackReadinessView> readinessList = new ArrayList<>();

        for (Long videoId : videoIds) {
            VideoPlaybackReadinessView view = readinessService.readiness(videoId);
            if (!problemOnly || isProblem(view)) {
                readinessList.add(view);
            }
        }

        return new VideoPlaybackReadinessBatchResult(
                true,
                videoIds.size(),
                countProblems(readinessList),
                0,
                0,
                readinessList,
                List.of()
        );
    }

    public VideoPlaybackReadinessBatchResult sync(VideoPlaybackReadinessBatchCommand command) {
        boolean dryRun = command == null || command.dryRun() == null || Boolean.TRUE.equals(command.dryRun());
        int safeLimit = normalizeLimit(command == null ? null : command.limit());
        boolean problemOnly = command == null || command.onlyProblem() == null || Boolean.TRUE.equals(command.onlyProblem());
        String reason = command == null || command.reason() == null || command.reason().isBlank()
                ? "batch playback readiness sync"
                : command.reason();

        List<Long> videoIds = loadVideoIds(safeLimit);
        List<VideoPlaybackReadinessView> readinessList = new ArrayList<>();
        List<VideoPlaybackReadinessSyncResult> syncResults = new ArrayList<>();

        int success = 0;
        int failed = 0;

        for (Long videoId : videoIds) {
            VideoPlaybackReadinessView view = readinessService.readiness(videoId);
            if (problemOnly && !isProblem(view)) {
                continue;
            }

            readinessList.add(view);

            try {
                VideoPlaybackReadinessSyncResult result = readinessService.sync(
                        videoId,
                        new VideoPlaybackReadinessSyncCommand(dryRun, reason)
                );
                syncResults.add(result);
                if (Boolean.TRUE.equals(result.success())) {
                    success++;
                } else {
                    failed++;
                }
            } catch (Exception ex) {
                failed++;
                syncResults.add(new VideoPlaybackReadinessSyncResult(
                        videoId,
                        dryRun,
                        false,
                        view.hlsReady(),
                        view.hlsManifestKey(),
                        view.videoStatus(),
                        view.videoStatus(),
                        List.of(),
                        List.of(),
                        "批量同步失败",
                        ex.getMessage()
                ));
            }
        }

        return new VideoPlaybackReadinessBatchResult(
                dryRun,
                videoIds.size(),
                countProblems(readinessList),
                success,
                failed,
                readinessList,
                syncResults
        );
    }

    private List<Long> loadVideoIds(int limit) {
        if (!tableExists("video")) {
            return List.of();
        }

        return jdbcTemplate.query(
                "SELECT id FROM video ORDER BY id DESC LIMIT ?",
                (rs, rowNum) -> rs.getLong("id"),
                limit
        );
    }

    private boolean isProblem(VideoPlaybackReadinessView view) {
        if (view == null) {
            return false;
        }

        if (!Boolean.TRUE.equals(view.videoExists())) {
            return false;
        }

        return Boolean.TRUE.equals(view.hlsReady())
                && !Boolean.TRUE.equals(view.playable());
    }

    private int countProblems(List<VideoPlaybackReadinessView> list) {
        int count = 0;
        for (VideoPlaybackReadinessView view : list) {
            if (isProblem(view)) {
                count++;
            }
        }
        return count;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 100;
        }
        return Math.min(limit, 500);
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
}
