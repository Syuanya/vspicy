package com.vspicy.video.service;

import com.vspicy.video.dto.AdminServiceHealthItemView;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import com.vspicy.video.dto.AdminServiceHealthSummaryView;
import com.vspicy.video.dto.VideoTranscodeDispatchView;
import com.vspicy.video.mq.VideoTranscodeDispatcher;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AdminServiceHealthService {
    private static final String UP = "UP";
    private static final String WARN = "WARN";
    private static final String DOWN = "DOWN";
    private static final String UNKNOWN = "UNKNOWN";

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationContext applicationContext;
    private final VideoTranscodeDispatcher transcodeDispatcher;
    private final Environment environment;
    private final MinioClient minioClient;

    public AdminServiceHealthService(
            JdbcTemplate jdbcTemplate,
            ApplicationContext applicationContext,
            VideoTranscodeDispatcher transcodeDispatcher,
            Environment environment,
            MinioClient minioClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationContext = applicationContext;
        this.transcodeDispatcher = transcodeDispatcher;
        this.environment = environment;
        this.minioClient = minioClient;
    }

    public AdminServiceHealthSummaryView summary() {
        List<AdminServiceHealthItemView> items = new ArrayList<>();

        items.add(checkMysql());
        items.add(checkRedis());
        items.add(checkMinio());
        items.add(checkRocketMq());
        items.add(checkFfmpeg());
        items.add(checkStorageRoot("videoTempRoot", "视频临时目录", firstProperty(
                "vspicy.video.storage.temp-root",
                "video.storage.temp-root",
                "vspicy.video.temp-root"
        )));
        items.add(checkStorageRoot("videoMergedRoot", "视频合并目录", firstProperty(
                "vspicy.video.storage.merged-root",
                "video.storage.merged-root",
                "vspicy.video.merged-root"
        )));
        items.add(checkStorageRoot("videoHlsRoot", "HLS 输出目录", firstProperty(
                "vspicy.video.storage.hls-root",
                "video.storage.hls-root",
                "vspicy.video.hls-root"
        )));

        int up = count(items, UP);
        int warn = count(items, WARN);
        int down = count(items, DOWN);
        int unknown = count(items, UNKNOWN);

        return new AdminServiceHealthSummaryView(
                LocalDateTime.now().toString(),
                overallStatus(down, warn, unknown),
                up,
                warn,
                down,
                unknown,
                items
        );
    }

    private AdminServiceHealthItemView checkMysql() {
        long start = System.currentTimeMillis();
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (value != null && value == 1) {
                return item(
                        "mysql",
                        "MySQL",
                        "database",
                        UP,
                        "数据库连接正常",
                        "无需处理",
                        start,
                        mapOf(
                                "validationQuery", "SELECT 1",
                                "url", maskJdbcUrl(firstProperty("spring.datasource.url"))
                        )
                );
            }

            return item(
                    "mysql",
                    "MySQL",
                    "database",
                    WARN,
                    "数据库返回异常结果",
                    "检查 datasource 配置和数据库状态",
                    start,
                    mapOf("validationQuery", "SELECT 1", "result", String.valueOf(value))
            );
        } catch (Exception ex) {
            return item(
                    "mysql",
                    "MySQL",
                    "database",
                    DOWN,
                    "数据库不可用：" + ex.getMessage(),
                    "检查 MySQL 是否启动、账号密码是否正确、schema 是否存在",
                    start,
                    mapOf("url", maskJdbcUrl(firstProperty("spring.datasource.url")))
            );
        }
    }

    /**
     * Redis is checked by reflection to avoid compile-time dependency on spring-data-redis.
     *
     * If this module does not include Redis dependencies, this method returns UNKNOWN instead of breaking compilation.
     */
    private AdminServiceHealthItemView checkRedis() {
        long start = System.currentTimeMillis();

        Object redisTemplate = findBean("stringRedisTemplate");
        if (redisTemplate == null) {
            redisTemplate = findBean("redisTemplate");
        }

        if (redisTemplate == null) {
            return item(
                    "redis",
                    "Redis",
                    "cache",
                    UNKNOWN,
                    "RedisTemplate Bean 不存在",
                    "当前模块没有 Redis Bean，或未引入 Redis 依赖；如果 video 服务不依赖 Redis 可忽略",
                    start,
                    mapOf(
                            "host", firstProperty("spring.data.redis.host", "spring.redis.host"),
                            "port", firstProperty("spring.data.redis.port", "spring.redis.port")
                    )
            );
        }

        Object connection = null;

        try {
            Method getConnectionFactory = redisTemplate.getClass().getMethod("getConnectionFactory");
            Object connectionFactory = getConnectionFactory.invoke(redisTemplate);

            if (connectionFactory == null) {
                return item(
                        "redis",
                        "Redis",
                        "cache",
                        DOWN,
                        "RedisConnectionFactory 不存在",
                        "检查 Redis 自动配置是否生效",
                        start,
                        mapOf()
                );
            }

            Method getConnection = connectionFactory.getClass().getMethod("getConnection");
            connection = getConnection.invoke(connectionFactory);

            if (connection == null) {
                return item(
                        "redis",
                        "Redis",
                        "cache",
                        DOWN,
                        "RedisConnection 为空",
                        "检查 Redis 连接工厂",
                        start,
                        mapOf()
                );
            }

            Object pong = invokeNoArg(connection, "ping");

            return item(
                    "redis",
                    "Redis",
                    "cache",
                    UP,
                    "Redis PING 正常：" + String.valueOf(pong),
                    "无需处理",
                    start,
                    mapOf(
                            "host", firstProperty("spring.data.redis.host", "spring.redis.host"),
                            "port", firstProperty("spring.data.redis.port", "spring.redis.port"),
                            "bean", redisTemplate.getClass().getName()
                    )
            );
        } catch (Exception ex) {
            return item(
                    "redis",
                    "Redis",
                    "cache",
                    DOWN,
                    "Redis 不可用：" + rootMessage(ex),
                    "检查 Redis 容器、端口、密码和 spring.data.redis 配置",
                    start,
                    mapOf(
                            "host", firstProperty("spring.data.redis.host", "spring.redis.host"),
                            "port", firstProperty("spring.data.redis.port", "spring.redis.port"),
                            "bean", redisTemplate.getClass().getName()
                    )
            );
        } finally {
            closeQuietly(connection);
        }
    }

    private Object findBean(String beanName) {
        try {
            if (applicationContext.containsBean(beanName)) {
                return applicationContext.getBean(beanName);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Object invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private void closeQuietly(Object target) {
        if (target == null) {
            return;
        }

        try {
            invokeNoArg(target, "close");
        } catch (Exception ignored) {
        }
    }

    private AdminServiceHealthItemView checkMinio() {
        long start = System.currentTimeMillis();
        String endpoint = firstProperty(
                "vspicy.video.minio.endpoint",
                "vspicy.minio.endpoint",
                "minio.endpoint",
                "minio.url",
                "storage.minio.endpoint"
        );
        String bucket = firstProperty(
                "vspicy.video.minio.bucket",
                "vspicy.video.minio.bucket-name",
                "vspicy.minio.bucket",
                "vspicy.minio.bucket-name",
                "minio.bucket",
                "minio.bucket-name",
                "storage.minio.bucket"
        );
        String accessKey = firstProperty(
                "vspicy.video.minio.access-key",
                "vspicy.video.minio.access-id",
                "vspicy.minio.access-key",
                "vspicy.minio.access-id",
                "minio.access-key",
                "minio.access-id",
                "storage.minio.access-key"
        );
        String publicBaseUrl = firstProperty(
                "vspicy.video.minio.public-base-url",
                "vspicy.minio.public-base-url",
                "minio.public-base-url"
        );

        if (isBlank(endpoint)) {
            return item(
                    "minio",
                    "MinIO",
                    "objectStorage",
                    UNKNOWN,
                    "MinIO endpoint 未配置",
                    "配置 vspicy.video.minio.endpoint 或 vspicy.minio.endpoint",
                    start,
                    mapOf("bucket", bucket, "accessKey", maskAccessKey(accessKey))
            );
        }

        if (isBlank(bucket)) {
            return item(
                    "minio",
                    "MinIO",
                    "objectStorage",
                    WARN,
                    "MinIO bucket 未配置",
                    "配置 VSPICY_MINIO_BUCKET 或 vspicy.video.minio.bucket",
                    start,
                    mapOf("endpoint", endpoint, "accessKey", maskAccessKey(accessKey))
            );
        }

        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucket)
                    .build());

            if (exists) {
                return item(
                        "minio",
                        "MinIO",
                        "objectStorage",
                        UP,
                        "MinIO 凭证可用，bucket 存在",
                        "无需处理",
                        start,
                        mapOf(
                                "endpoint", endpoint,
                                "bucket", bucket,
                                "accessKey", maskAccessKey(accessKey),
                                "publicBaseUrl", publicBaseUrl
                        )
                );
            }

            return item(
                    "minio",
                    "MinIO",
                    "objectStorage",
                    WARN,
                    "MinIO 凭证可用，但 bucket 不存在：" + bucket,
                    "登录 MinIO 控制台创建 bucket，或修正 VSPICY_MINIO_BUCKET",
                    start,
                    mapOf(
                            "endpoint", endpoint,
                            "bucket", bucket,
                            "accessKey", maskAccessKey(accessKey),
                            "publicBaseUrl", publicBaseUrl
                    )
            );
        } catch (Exception ex) {
            return item(
                    "minio",
                    "MinIO",
                    "objectStorage",
                    DOWN,
                    "MinIO 不可用：" + rootMessage(ex),
                    "检查 9000 端口、accessKey/secretKey、bucket 和 vspicy.video.minio 配置；9001 仅用于控制台",
                    start,
                    mapOf(
                            "endpoint", endpoint,
                            "bucket", bucket,
                            "accessKey", maskAccessKey(accessKey),
                            "publicBaseUrl", publicBaseUrl
                    )
            );
        }
    }

    private AdminServiceHealthItemView checkRocketMq() {
        long start = System.currentTimeMillis();
        try {
            VideoTranscodeDispatchView health = transcodeDispatcher.health();
            boolean templateAvailable = Boolean.TRUE.equals(health.rocketMqTemplateAvailable());
            boolean rocketEnabled = Boolean.TRUE.equals(health.rocketMqEnabled());
            boolean fallbackEnabled = Boolean.TRUE.equals(health.fallbackLocalEnabled());

            if (templateAvailable && rocketEnabled) {
                return item(
                        "rocketmq",
                        "RocketMQ",
                        "messageQueue",
                        UP,
                        "RocketMQTemplate 可用",
                        "无需处理；如 topic 不存在，请执行 topic 创建脚本",
                        start,
                        mapOf(
                                "destination", health.destination(),
                                "fallbackLocalEnabled", String.valueOf(fallbackEnabled)
                        )
                );
            }

            if (!templateAvailable && fallbackEnabled) {
                return item(
                        "rocketmq",
                        "RocketMQ",
                        "messageQueue",
                        WARN,
                        "RocketMQTemplate 不可用，但本地 fallback 已启用",
                        "上传转码不会被 MQ 阻断；如需 MQ，检查 RocketMQ starter、nameserver、producer 配置",
                        start,
                        mapOf(
                                "destination", health.destination(),
                                "fallbackLocalEnabled", String.valueOf(fallbackEnabled)
                        )
                );
            }

            return item(
                    "rocketmq",
                    "RocketMQ",
                    "messageQueue",
                    DOWN,
                    "RocketMQ 不可用，且 fallback 状态不安全",
                    "启用 fallback-local-enabled 或修复 RocketMQ 配置",
                    start,
                    mapOf(
                            "destination", health.destination(),
                            "message", health.message(),
                            "errorMessage", health.errorMessage()
                    )
            );
        } catch (Exception ex) {
            return item(
                    "rocketmq",
                    "RocketMQ",
                    "messageQueue",
                    WARN,
                    "RocketMQ 健康检查失败：" + ex.getMessage(),
                    "检查 VideoTranscodeDispatcher 是否正常注册",
                    start,
                    mapOf()
            );
        }
    }

    private AdminServiceHealthItemView checkFfmpeg() {
        long start = System.currentTimeMillis();
        String ffmpegPath = firstProperty(
                "vspicy.video.storage.ffmpeg-path",
                "vspicy.video.ffmpeg-path",
                "video.storage.ffmpeg-path",
                "video.ffmpeg-path"
        );
        if (isBlank(ffmpegPath)) {
            ffmpegPath = "ffmpeg";
        }

        try {
            Process process = new ProcessBuilder(ffmpegPath, "-version")
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return item(
                        "ffmpeg",
                        "FFmpeg",
                        "transcode",
                        DOWN,
                        "FFmpeg 执行超时",
                        "检查 ffmpeg 路径是否正确，建议配置绝对路径",
                        start,
                        mapOf("ffmpegPath", ffmpegPath)
                );
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return item(
                        "ffmpeg",
                        "FFmpeg",
                        "transcode",
                        UP,
                        "FFmpeg 可执行",
                        "无需处理",
                        start,
                        mapOf("ffmpegPath", ffmpegPath, "exitCode", String.valueOf(exitCode))
                );
            }

            return item(
                    "ffmpeg",
                    "FFmpeg",
                    "transcode",
                    DOWN,
                    "FFmpeg 返回非 0 退出码：" + exitCode,
                    "检查 ffmpeg 安装和执行权限",
                    start,
                    mapOf("ffmpegPath", ffmpegPath, "exitCode", String.valueOf(exitCode))
            );
        } catch (Exception ex) {
            return item(
                    "ffmpeg",
                    "FFmpeg",
                    "transcode",
                    DOWN,
                    "FFmpeg 不可执行：" + ex.getMessage(),
                    "安装 ffmpeg 或配置 vspicy.video.storage.ffmpeg-path 为绝对路径",
                    start,
                    mapOf("ffmpegPath", ffmpegPath)
            );
        }
    }

    private AdminServiceHealthItemView checkStorageRoot(String key, String title, String path) {
        long start = System.currentTimeMillis();
        if (isBlank(path)) {
            return item(
                    key,
                    title,
                    "filesystem",
                    UNKNOWN,
                    "目录未配置",
                    "配置对应的视频存储目录",
                    start,
                    mapOf()
            );
        }

        try {
            File file = new File(path);
            if (!file.exists()) {
                boolean created = file.mkdirs();
                if (!created && !file.exists()) {
                    return item(
                            key,
                            title,
                            "filesystem",
                            DOWN,
                            "目录不存在且创建失败",
                            "检查父目录权限或手动创建目录",
                            start,
                            mapOf("path", file.getAbsolutePath())
                    );
                }
            }

            if (!file.isDirectory()) {
                return item(
                        key,
                        title,
                        "filesystem",
                        DOWN,
                        "路径不是目录",
                        "修正配置为目录路径",
                        start,
                        mapOf("path", file.getAbsolutePath())
                );
            }

            boolean readable = file.canRead();
            boolean writable = file.canWrite();

            if (readable && writable) {
                return item(
                        key,
                        title,
                        "filesystem",
                        UP,
                        "目录存在且可读写",
                        "无需处理",
                        start,
                        mapOf("path", file.getAbsolutePath())
                );
            }

            return item(
                    key,
                    title,
                    "filesystem",
                    WARN,
                    "目录权限不完整",
                    "检查目录读写权限",
                    start,
                    mapOf(
                            "path", file.getAbsolutePath(),
                            "readable", String.valueOf(readable),
                            "writable", String.valueOf(writable)
                    )
            );
        } catch (Exception ex) {
            return item(
                    key,
                    title,
                    "filesystem",
                    DOWN,
                    "目录检查失败：" + ex.getMessage(),
                    "检查路径配置和权限",
                    start,
                    mapOf("path", path)
            );
        }
    }

    private AdminServiceHealthItemView item(
            String key,
            String title,
            String groupKey,
            String status,
            String message,
            String suggestion,
            long start,
            Map<String, String> details
    ) {
        return new AdminServiceHealthItemView(
                key,
                title,
                groupKey,
                status,
                level(status),
                message,
                suggestion,
                System.currentTimeMillis() - start,
                details == null ? Map.of() : details
        );
    }

    private String overallStatus(int down, int warn, int unknown) {
        if (down > 0) {
            return DOWN;
        }
        if (warn > 0) {
            return WARN;
        }
        if (unknown > 0) {
            return UNKNOWN;
        }
        return UP;
    }

    private String level(String status) {
        if (UP.equals(status)) {
            return "success";
        }
        if (WARN.equals(status)) {
            return "warning";
        }
        if (DOWN.equals(status)) {
            return "danger";
        }
        return "info";
    }

    private int count(List<AdminServiceHealthItemView> items, String status) {
        int count = 0;
        for (AdminServiceHealthItemView item : items) {
            if (status.equals(item.status())) {
                count++;
            }
        }
        return count;
    }

    private String firstProperty(String... keys) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private Map<String, String> mapOf(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (!isBlank(kv[i + 1])) {
                map.put(kv[i], kv[i + 1]);
            }
        }
        return map;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String maskJdbcUrl(String url) {
        if (isBlank(url)) {
            return "";
        }
        return url.replaceAll("password=[^&;]+", "password=****");
    }

    private String maskAccessKey(String value) {
        if (isBlank(value)) {
            return "";
        }
        if (value.length() <= 2) {
            return "**";
        }
        if (value.length() <= 6) {
            return value.charAt(0) + "***" + value.charAt(value.length() - 1);
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    private String rootMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? ex.getMessage() : cause.getMessage();
    }
}
