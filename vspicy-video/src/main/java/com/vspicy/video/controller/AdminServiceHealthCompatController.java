package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.config.VideoStorageProperties;
import com.vspicy.video.mq.VideoTranscodeDispatcher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@ConditionalOnMissingBean(name = "adminServiceHealthController")
@RequestMapping("/api/videos/admin/service-health")
public class AdminServiceHealthCompatController {
    private final JdbcTemplate jdbcTemplate;
    private final VideoTranscodeDispatcher dispatcher;
    private final VideoStorageProperties storageProperties;

    public AdminServiceHealthCompatController(JdbcTemplate jdbcTemplate, VideoTranscodeDispatcher dispatcher, VideoStorageProperties storageProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.dispatcher = dispatcher;
        this.storageProperties = storageProperties;
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(mysql());
        items.add(rocketmq());
        items.add(ffmpeg());
        items.add(root("videoTempRoot", "视频临时目录", storageProperties.getTempRoot()));
        items.add(root("videoMergedRoot", "视频合并目录", storageProperties.getMergedRoot()));
        items.add(root("videoHlsRoot", "HLS 输出目录", storageProperties.getHlsRoot()));
        int up=0,warn=0,down=0,unknown=0;
        for (Map<String,Object> item:items){String s=String.valueOf(item.get("status")); if("UP".equals(s))up++; else if("WARN".equals(s))warn++; else if("DOWN".equals(s))down++; else unknown++;}
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("generatedAt", LocalDateTime.now().toString());
        data.put("overallStatus", down>0?"DOWN":warn>0?"WARN":unknown>0?"UNKNOWN":"UP");
        data.put("upCount", up); data.put("warnCount", warn); data.put("downCount", down); data.put("unknownCount", unknown); data.put("items", items);
        return Result.ok(data);
    }

    private Map<String,Object> mysql(){long st=System.currentTimeMillis(); try{jdbcTemplate.queryForObject("SELECT 1", Integer.class); return item("mysql","MySQL","database","UP","数据库连接正常","无需处理",st,Map.of("validationQuery","SELECT 1"));}catch(Exception e){return item("mysql","MySQL","database","DOWN","数据库不可用："+e.getMessage(),"检查 MySQL 配置",st,Map.of());}}
    private Map<String,Object> rocketmq(){long st=System.currentTimeMillis(); try{var h=dispatcher.health(); boolean ok=Boolean.TRUE.equals(h.rocketMqTemplateAvailable()); return item("rocketmq","RocketMQ","messageQueue",ok?"UP":"WARN",h.message(),"不可用时会 fallback 本地转码",st,Map.of("destination",String.valueOf(h.destination())));}catch(Exception e){return item("rocketmq","RocketMQ","messageQueue","WARN",e.getMessage(),"检查 RocketMQ 配置",st,Map.of());}}
    private Map<String,Object> ffmpeg(){long st=System.currentTimeMillis(); String path=storageProperties.getFfmpegPath()==null?"ffmpeg":storageProperties.getFfmpegPath(); try{Process p=new ProcessBuilder(path,"-version").redirectErrorStream(true).start(); boolean done=p.waitFor(5,java.util.concurrent.TimeUnit.SECONDS); if(!done){p.destroyForcibly(); return item("ffmpeg","FFmpeg","transcode","DOWN","FFmpeg 执行超时","配置 ffmpeg 绝对路径",st,Map.of("ffmpegPath",path));} return item("ffmpeg","FFmpeg","transcode",p.exitValue()==0?"UP":"DOWN","FFmpeg exitCode="+p.exitValue(),"检查 FFmpeg 安装",st,Map.of("ffmpegPath",path));}catch(Exception e){return item("ffmpeg","FFmpeg","transcode","DOWN","FFmpeg 不可执行："+e.getMessage(),"安装 FFmpeg 或配置绝对路径",st,Map.of("ffmpegPath",path));}}
    private Map<String,Object> root(String key,String title,String path){long st=System.currentTimeMillis(); if(path==null||path.isBlank()) return item(key,title,"filesystem","UNKNOWN","目录未配置","配置视频存储目录",st,Map.of()); File f=new File(path); if(!f.exists()) f.mkdirs(); String status=f.exists()&&f.isDirectory()&&f.canRead()&&f.canWrite()?"UP":"WARN"; return item(key,title,"filesystem",status,status.equals("UP")?"目录存在且可读写":"目录权限不完整","检查目录权限",st,Map.of("path",f.getAbsolutePath()));}
    private Map<String,Object> item(String key,String title,String group,String status,String message,String suggestion,long st,Map<String,String> details){Map<String,Object> m=new LinkedHashMap<>(); m.put("key",key);m.put("title",title);m.put("groupKey",group);m.put("status",status);m.put("level",level(status));m.put("message",message);m.put("suggestion",suggestion);m.put("durationMs",System.currentTimeMillis()-st);m.put("details",details);return m;}
    private String level(String s){return "UP".equals(s)?"success":"WARN".equals(s)?"warning":"DOWN".equals(s)?"danger":"info";}
}
