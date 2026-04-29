package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.VideoTranscodeDispatchView;
import com.vspicy.video.mq.VideoTranscodeDispatcher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@ConditionalOnMissingBean(name = "videoTranscodeStateController")
@RequestMapping("/api/videos/transcode/state")
public class VideoTranscodeStateCompatController {
    private final JdbcTemplate jdbcTemplate;
    private final VideoTranscodeDispatcher dispatcher;

    public VideoTranscodeStateCompatController(JdbcTemplate jdbcTemplate, VideoTranscodeDispatcher dispatcher) {
        this.jdbcTemplate = jdbcTemplate;
        this.dispatcher = dispatcher;
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalCount", count(null));
        m.put("pendingCount", count("PENDING"));
        m.put("dispatchedCount", count("DISPATCHED"));
        m.put("runningCount", count("RUNNING"));
        m.put("successCount", count("SUCCESS"));
        m.put("failedCount", count("FAILED"));
        m.put("canceledCount", count("CANCELED"));
        m.put("retryExhaustedCount", 0L);
        return Result.ok(m);
    }

    @GetMapping("/tasks")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        int safeLimit = normalizeLimit(limit);
        List<Object> args = new ArrayList<>();
        String sql = "SELECT id, video_id, source_file_path, status, retry_count, error_message, started_at, finished_at, created_at, updated_at FROM video_transcode_task";
        if (status != null && !status.isBlank()) {
            sql += " WHERE status = ?";
            args.add(status);
        }
        sql += " ORDER BY id DESC LIMIT ?";
        args.add(safeLimit);

        return Result.ok(jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("videoId", nullableLong(rs.getObject("video_id")));
            m.put("sourceFilePath", rs.getString("source_file_path"));
            m.put("status", rs.getString("status"));
            m.put("retryCount", rs.getInt("retry_count"));
            m.put("maxRetryCount", 5);
            m.put("errorMessage", rs.getString("error_message"));
            m.put("lastDispatchMode", null);
            m.put("lastDispatchError", null);
            m.put("dispatchedAt", null);
            m.put("startedAt", string(rs.getObject("started_at")));
            m.put("finishedAt", string(rs.getObject("finished_at")));
            m.put("canceledAt", null);
            m.put("createdAt", string(rs.getObject("created_at")));
            m.put("updatedAt", string(rs.getObject("updated_at")));
            return m;
        }, args.toArray()));
    }

    @PostMapping("/{id}/retry")
    public Result<VideoTranscodeDispatchView> retry(@PathVariable Long id) {
        incrementRetryAndPending(id);
        return Result.ok(dispatcher.dispatch(id, videoIdOfTask(id)));
    }

    @PostMapping("/{id}/rerun")
    public Result<VideoTranscodeDispatchView> rerun(@PathVariable Long id) {
        incrementRetryAndPending(id);
        return Result.ok(dispatcher.dispatch(id, videoIdOfTask(id)));
    }

    @PostMapping("/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable Long id) {
        updateStatus(id, "CANCELED", "manual cancel");
        return Result.ok(task(id));
    }

    @PostMapping("/{id}/reset")
    public Result<Map<String, Object>> reset(@PathVariable Long id) {
        updateStatus(id, "PENDING", null);
        return Result.ok(task(id));
    }

    @PostMapping("/{id}/success")
    public Result<Map<String, Object>> success(@PathVariable Long id) {
        updateStatus(id, "SUCCESS", null);
        return Result.ok(task(id));
    }

    @PostMapping("/{id}/fail")
    public Result<Map<String, Object>> fail(@PathVariable Long id) {
        updateStatus(id, "FAILED", "manual fail");
        return Result.ok(task(id));
    }

    private void incrementRetryAndPending(Long id) {
        jdbcTemplate.update("UPDATE video_transcode_task SET status='PENDING', retry_count=COALESCE(retry_count,0)+1, error_message=NULL WHERE id=?", id);
    }

    private void updateStatus(Long id, String status, String error) {
        jdbcTemplate.update("UPDATE video_transcode_task SET status=?, error_message=? WHERE id=?", status, error, id);
    }

    private Long count(String status) {
        Long v = status == null
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM video_transcode_task", Long.class)
                : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM video_transcode_task WHERE status=?", Long.class, status);
        return v == null ? 0L : v;
    }

    private Long videoIdOfTask(Long id) {
        List<Long> rows = jdbcTemplate.query("SELECT video_id FROM video_transcode_task WHERE id=? LIMIT 1", (rs, i) -> nullableLong(rs.getObject("video_id")), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> task(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query("SELECT id, video_id, source_file_path, status, retry_count, error_message, started_at, finished_at, created_at, updated_at FROM video_transcode_task WHERE id=? LIMIT 1", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("videoId", nullableLong(rs.getObject("video_id")));
            m.put("sourceFilePath", rs.getString("source_file_path"));
            m.put("status", rs.getString("status"));
            m.put("retryCount", rs.getInt("retry_count"));
            m.put("maxRetryCount", 5);
            m.put("errorMessage", rs.getString("error_message"));
            m.put("startedAt", string(rs.getObject("started_at")));
            m.put("finishedAt", string(rs.getObject("finished_at")));
            m.put("createdAt", string(rs.getObject("created_at")));
            m.put("updatedAt", string(rs.getObject("updated_at")));
            return m;
        }, id);
        return rows.isEmpty() ? Map.of("id", id, "missing", true) : rows.get(0);
    }

    private int normalizeLimit(Integer limit) { return limit == null || limit <= 0 || limit > 500 ? 100 : limit; }
    private Long nullableLong(Object v) { return v == null ? null : ((Number) v).longValue(); }
    private String string(Object v) { return v == null ? null : String.valueOf(v); }
}
