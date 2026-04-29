package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@ConditionalOnMissingBean(name = {"videoPlaybackReadinessBatchController", "playbackReadinessBatchController"})
@RequestMapping("/api/videos/playback/readiness-batch")
public class PlaybackReadinessBatchCompatController {
    private final JdbcTemplate jdbcTemplate;

    public PlaybackReadinessBatchCompatController(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @GetMapping("/scan")
    public Result<Map<String,Object>> scan(@RequestParam(defaultValue="100") Integer limit, @RequestParam(defaultValue="true") Boolean onlyProblem) {
        return Result.ok(scanInternal(limit, onlyProblem, true));
    }

    @PostMapping("/sync")
    public Result<Map<String,Object>> sync(@RequestBody(required=false) Map<String,Object> body) {
        boolean dryRun = body == null || !Boolean.FALSE.equals(body.get("dryRun"));
        int limit = body != null && body.get("limit") instanceof Number ? ((Number) body.get("limit")).intValue() : 100;
        Map<String,Object> result = scanInternal(limit, true, dryRun);
        result.put("dryRun", dryRun);
        result.put("syncResults", List.of());
        return Result.ok(result);
    }

    private Map<String,Object> scanInternal(Integer limit, Boolean onlyProblem, boolean dryRun){
        int safe=limit==null||limit<=0||limit>500?100:limit;
        List<Map<String,Object>> list=new ArrayList<>();
        if(tableExists("video")){
            List<Long> ids=jdbcTemplate.query("SELECT id FROM video ORDER BY id DESC LIMIT ?", (rs,i)->rs.getLong("id"), safe);
            for(Long id:ids){Map<String,Object> v=view(id); if(!Boolean.TRUE.equals(onlyProblem)||Boolean.TRUE.equals(v.get("hlsReady"))&&!Boolean.TRUE.equals(v.get("playable"))) list.add(v);}
            Map<String,Object> r=new LinkedHashMap<>(); r.put("dryRun", dryRun); r.put("scannedCount", ids.size()); r.put("problemCount", list.size()); r.put("successCount",0); r.put("failedCount",0); r.put("readinessList", list); r.put("syncResults", List.of()); return r;
        }
        Map<String,Object> r=new LinkedHashMap<>(); r.put("dryRun", dryRun); r.put("scannedCount",0); r.put("problemCount",0); r.put("successCount",0); r.put("failedCount",0); r.put("readinessList", list); r.put("syncResults", List.of()); return r;
    }

    private Map<String,Object> view(Long videoId){String status=videoStatus(videoId); String hls=findHls(videoId); boolean hlsReady=hls!=null; boolean playable=hlsReady && ("PUBLISHED".equalsIgnoreCase(status)||"SUCCESS".equalsIgnoreCase(status)); Map<String,Object> m=new LinkedHashMap<>(); m.put("videoId",videoId);m.put("videoExists",true);m.put("videoStatus",status);m.put("hlsReady",hlsReady);m.put("hlsManifestKey",hls);m.put("statusPublished",playable);m.put("playbackUrlPresent",hlsReady);m.put("playbackUrl",hls);m.put("playable",playable);m.put("message",playable?"视频播放就绪":"HLS 或 video 状态未完全就绪");m.put("suggestedAction",playable?"PLAY":hlsReady?"SYNC":"WAIT_OR_RERUN");m.put("detectedPlaybackColumns",List.of()); return m;}
    private String videoStatus(Long id){try{return jdbcTemplate.queryForObject("SELECT status FROM video WHERE id=?", String.class, id);}catch(Exception e){return null;}}
    private String findHls(Long id){if(!tableExists("video_file")||!columnExists("video_file","video_id")) return null; for(String c:List.of("object_key","file_path","url")){if(columnExists("video_file",c)){List<String> rows=jdbcTemplate.query("SELECT `"+c+"` FROM video_file WHERE video_id=? AND `"+c+"` LIKE '%.m3u8%' ORDER BY id DESC LIMIT 1", (rs,i)->rs.getString(c), id); if(!rows.isEmpty()&&rows.get(0)!=null&&!rows.get(0).isBlank()) return rows.get(0);}} return null;}
    private boolean tableExists(String table){try{Long v=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",Long.class,table);return v!=null&&v>0;}catch(Exception e){return false;}}
    private boolean columnExists(String table,String column){try{Long v=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?",Long.class,table,column);return v!=null&&v>0;}catch(Exception e){return false;}}
}
