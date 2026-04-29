package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@ConditionalOnMissingBean(name = "operationAuditController")
@RequestMapping("/api/videos/admin/operation-audit")
public class OperationAuditCompatController {
    private final JdbcTemplate jdbcTemplate;
    public OperationAuditCompatController(JdbcTemplate jdbcTemplate){this.jdbcTemplate=jdbcTemplate;}

    @GetMapping("/stats")
    public Result<Map<String,Object>> stats(){ensure(); Map<String,Object> m=new LinkedHashMap<>(); m.put("totalCount",count("SELECT COUNT(*) FROM operation_audit_log")); m.put("todayCount",count("SELECT COUNT(*) FROM operation_audit_log WHERE created_at>=CURDATE()")); m.put("transcodeActionCount",count("SELECT COUNT(*) FROM operation_audit_log WHERE action LIKE 'TRANSCODE_%'")); m.put("playbackActionCount",count("SELECT COUNT(*) FROM operation_audit_log WHERE action LIKE 'PLAYBACK_%'")); m.put("cleanupActionCount",count("SELECT COUNT(*) FROM operation_audit_log WHERE action LIKE 'CLEANUP_%'")); m.put("hlsRepairActionCount",count("SELECT COUNT(*) FROM operation_audit_log WHERE action LIKE 'HLS_REPAIR_%'")); m.put("storageActionCount",count("SELECT COUNT(*) FROM operation_audit_log WHERE action LIKE 'STORAGE_%'")); return Result.ok(m);}

    @GetMapping("/list")
    public Result<List<Map<String,Object>>> list(@RequestParam(required=false) String action,@RequestParam(required=false) String targetType,@RequestParam(required=false) Long operatorId,@RequestParam(defaultValue="100") Integer limit){ensure(); int safe=limit==null||limit<=0||limit>500?100:limit; List<Object> args=new ArrayList<>(); String sql="SELECT * FROM operation_audit_log WHERE 1=1"; if(action!=null&&!action.isBlank()){sql+=" AND action=?";args.add(action);} if(targetType!=null&&!targetType.isBlank()){sql+=" AND target_type=?";args.add(targetType);} if(operatorId!=null){sql+=" AND operator_id=?";args.add(operatorId);} sql+=" ORDER BY id DESC LIMIT ?";args.add(safe); return Result.ok(jdbcTemplate.query(sql,(rs,i)->{Map<String,Object> m=new LinkedHashMap<>(); m.put("id",rs.getLong("id"));m.put("action",rs.getString("action"));m.put("targetType",rs.getString("target_type"));m.put("targetId",rs.getString("target_id"));m.put("operatorId",rs.getObject("operator_id"));m.put("operatorName",rs.getString("operator_name"));m.put("description",rs.getString("description"));m.put("requestIp",rs.getString("request_ip"));m.put("userAgent",rs.getString("user_agent"));m.put("detailJson",rs.getString("detail_json"));m.put("createdAt",String.valueOf(rs.getObject("created_at")));return m;},args.toArray()));}

    @PostMapping("/record")
    public Result<Map<String,Object>> record(@RequestBody Map<String,Object> body, HttpServletRequest request){ensure(); jdbcTemplate.update("INSERT INTO operation_audit_log(action,target_type,target_id,operator_id,operator_name,description,request_ip,user_agent,detail_json) VALUES(?,?,?,?,?,?,?,?,?)", str(body.get("action")), str(body.get("targetType")), str(body.get("targetId")), body.get("operatorId"), str(body.get("operatorName")), str(body.get("description")), request==null?null:request.getRemoteAddr(), request==null?null:request.getHeader("User-Agent"), str(body.get("detailJson"))); Long id=jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()",Long.class); return Result.ok(Map.of("id",id==null?0L:id,"action",str(body.get("action"))));}

    private Long count(String sql){Long v=jdbcTemplate.queryForObject(sql,Long.class);return v==null?0L:v;}
    private String str(Object v){return v==null?null:String.valueOf(v);}    
    private void ensure(){jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS operation_audit_log (id BIGINT PRIMARY KEY AUTO_INCREMENT, action VARCHAR(128) NOT NULL, target_type VARCHAR(128), target_id VARCHAR(128), operator_id BIGINT, operator_name VARCHAR(128), description VARCHAR(512), request_ip VARCHAR(64), user_agent VARCHAR(512), detail_json TEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_action_created(action, created_at), KEY idx_target(target_type, target_id), KEY idx_operator_created(operator_id, created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");}
}
