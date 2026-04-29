package com.vspicy.video.controller;

import com.vspicy.common.core.Result;
import com.vspicy.video.dto.ConfigDiagnosticView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/videos/dev")
@ConditionalOnProperty(prefix = "vspicy.dev.config-diagnostics", name = "enabled", havingValue = "true")
public class ConfigDiagnosticsController {
    private final Environment environment;

    public ConfigDiagnosticsController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/config-diagnostics")
    public Result<ConfigDiagnosticView> diagnostics() {
        return Result.ok(new ConfigDiagnosticView(
                Arrays.asList(environment.getActiveProfiles()),
                mapOf(
                        "url", value("spring.datasource.url"),
                        "username", mask(value("spring.datasource.username")),
                        "password", mask(value("spring.datasource.password"))
                ),
                mapOf(
                        "host", value("spring.data.redis.host", "spring.redis.host"),
                        "port", value("spring.data.redis.port", "spring.redis.port"),
                        "password", mask(value("spring.data.redis.password", "spring.redis.password"))
                ),
                mapOf(
                        "endpoint", value("vspicy.minio.endpoint", "minio.endpoint"),
                        "bucket", value("vspicy.minio.bucket", "minio.bucket"),
                        "accessKey", mask(value("vspicy.minio.access-key", "minio.access-key")),
                        "secretKey", mask(value("vspicy.minio.secret-key", "minio.secret-key"))
                ),
                mapOf(
                        "nameServer", value("rocketmq.name-server", "rocketmq.nameServer", "rocketmq.namesrvAddr"),
                        "producerGroup", value("rocketmq.producer.group")
                ),
                mapOf(
                        "tempRoot", value("vspicy.video.storage.temp-root", "video.storage.temp-root"),
                        "mergedRoot", value("vspicy.video.storage.merged-root", "video.storage.merged-root"),
                        "hlsRoot", value("vspicy.video.storage.hls-root", "video.storage.hls-root"),
                        "ffmpegPath", value("vspicy.video.storage.ffmpeg-path", "vspicy.video.ffmpeg-path")
                ),
                mapOf(
                        "enabled", value("vspicy.dev.config-diagnostics.enabled"),
                        "note", "sensitive values are masked"
                )
        ));
    }

    private String value(String... keys) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    private Map<String, String> mapOf(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return map;
    }
}
