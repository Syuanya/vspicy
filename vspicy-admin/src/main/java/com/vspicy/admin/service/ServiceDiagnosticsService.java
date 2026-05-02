package com.vspicy.admin.service;

import com.vspicy.admin.dto.ServiceDiagnosticsOverviewView;
import com.vspicy.admin.dto.ServiceProbeView;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class ServiceDiagnosticsService {
    private static final String LOCALHOST = "127.0.0.1";
    private static final int TIMEOUT_MILLIS = 800;

    public ServiceDiagnosticsOverviewView overview() {
        List<ServiceProbeView> services = serviceTargets().stream().map(this::probe).toList();
        List<ServiceProbeView> middlewares = middlewareTargets().stream().map(this::probe).toList();
        return new ServiceDiagnosticsOverviewView(
                System.currentTimeMillis(),
                services.size(),
                count(services, "UP"),
                count(services, "DOWN"),
                middlewares.size(),
                count(middlewares, "UP"),
                count(middlewares, "DOWN"),
                services,
                middlewares,
                suggestions(services, middlewares),
                powershellChecks()
        );
    }

    private ServiceProbeView probe(Target target) {
        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target.host(), target.port()), TIMEOUT_MILLIS);
            long latency = Duration.ofNanos(System.nanoTime() - started).toMillis();
            return new ServiceProbeView(target.code(), target.name(), target.category(), target.host(), target.port(),
                    target.endpoint(), "UP", latency, target.required(), target.ownerModule(), "端口可连接", target.startHint());
        } catch (Exception ex) {
            long latency = Duration.ofNanos(System.nanoTime() - started).toMillis();
            return new ServiceProbeView(target.code(), target.name(), target.category(), target.host(), target.port(),
                    target.endpoint(), "DOWN", latency, target.required(), target.ownerModule(), safeMessage(ex), target.startHint());
        }
    }

    private List<Target> serviceTargets() {
        return List.of(
                new Target("gateway", "Gateway 网关", "service", LOCALHOST, intValue("VSPICY_GATEWAY_PORT", 18080), "http://127.0.0.1:18080", true, "vspicy-gateway", "启动 GatewayApplication，确认 Netty started on port 18080"),
                new Target("auth", "认证服务", "service", LOCALHOST, intValue("VSPICY_AUTH_PORT", 18081), "http://127.0.0.1:18081", true, "vspicy-auth", "启动 AuthApplication"),
                new Target("user", "用户服务", "service", LOCALHOST, intValue("VSPICY_USER_PORT", 18082), "http://127.0.0.1:18082", true, "vspicy-user", "启动 UserApplication"),
                new Target("file", "文件服务", "service", LOCALHOST, intValue("VSPICY_FILE_PORT", 18083), "http://127.0.0.1:18083", false, "vspicy-file", "启动 FileApplication"),
                new Target("video", "视频服务", "service", LOCALHOST, intValue("VSPICY_VIDEO_PORT", 18084), "http://127.0.0.1:18084", true, "vspicy-video", "启动 VideoApplication"),
                new Target("content", "内容服务", "service", LOCALHOST, intValue("VSPICY_CONTENT_PORT", 18085), "http://127.0.0.1:18085", true, "vspicy-content", "启动 ContentApplication"),
                new Target("interaction", "互动服务", "service", LOCALHOST, intValue("VSPICY_INTERACTION_PORT", 18086), "http://127.0.0.1:18086", false, "vspicy-interaction", "启动 InteractionApplication；若本地实际是 18088，请同步 Gateway 路由"),
                new Target("recommend", "推荐服务", "service", LOCALHOST, intValue("VSPICY_RECOMMEND_PORT", 18087), "http://127.0.0.1:18087", false, "vspicy-recommend", "启动 RecommendApplication，并确认 Redis 密码正确"),
                new Target("profile", "画像服务", "service", LOCALHOST, intValue("VSPICY_PROFILE_PORT", 18088), "http://127.0.0.1:18088", false, "vspicy-profile", "启动 ProfileApplication"),
                new Target("dashboard", "数据看板服务", "service", LOCALHOST, intValue("VSPICY_DASHBOARD_PORT", 18089), "http://127.0.0.1:18089", false, "vspicy-dashboard", "启动 DashboardApplication"),
                new Target("admin", "后台管理服务", "service", LOCALHOST, intValue("VSPICY_ADMIN_PORT", 18090), "http://127.0.0.1:18090", true, "vspicy-admin", "启动 AdminApplication"),
                new Target("notification", "通知服务", "service", LOCALHOST, intValue("VSPICY_NOTIFICATION_PORT", 18091), "http://127.0.0.1:18091", true, "vspicy-notification", "启动 NotificationApplication"),
                new Target("member", "会员服务", "service", LOCALHOST, intValue("VSPICY_MEMBER_PORT", 18092), "http://127.0.0.1:18092", false, "vspicy-member", "启动 MemberApplication")
        );
    }

    private List<Target> middlewareTargets() {
        return List.of(
                new Target("mysql", "MySQL", "middleware", env("VSPICY_DB_HOST", LOCALHOST), intValue("VSPICY_DB_PORT", 3306), "127.0.0.1:3306", true, "docker:mysql", "docker start vspicy-mysql"),
                new Target("redis", "Redis", "middleware", env("VSPICY_REDIS_HOST", LOCALHOST), intValue("VSPICY_REDIS_PORT", 6379), "127.0.0.1:6379", true, "docker:redis", "docker start vspicy-redis；密码默认 redis123456"),
                new Target("nacos", "Nacos", "middleware", LOCALHOST, intValue("VSPICY_NACOS_PORT", 8848), "http://127.0.0.1:8848/nacos", true, "docker:nacos", "docker start vspicy-nacos"),
                new Target("minio-api", "MinIO API", "middleware", LOCALHOST, intValue("VSPICY_MINIO_API_PORT", 9000), "http://127.0.0.1:9000", true, "docker:minio", "docker start vspicy-minio；后端 SDK 必须使用 9000"),
                new Target("minio-console", "MinIO Console", "middleware", LOCALHOST, intValue("VSPICY_MINIO_CONSOLE_PORT", 9001), "http://127.0.0.1:9001", false, "docker:minio", "浏览器控制台端口，账号 vspicy / vspicy123456"),
                new Target("rocketmq-namesrv", "RocketMQ NameServer", "middleware", LOCALHOST, intValue("VSPICY_ROCKETMQ_NAMESRV_PORT", 9876), "127.0.0.1:9876", false, "docker:rocketmq", "docker start vspicy-rocketmq-namesrv"),
                new Target("rocketmq-broker", "RocketMQ Broker", "middleware", LOCALHOST, intValue("VSPICY_ROCKETMQ_BROKER_PORT", 10911), "127.0.0.1:10911", false, "docker:rocketmq", "docker start vspicy-rocketmq-broker")
        );
    }

    private List<String> suggestions(List<ServiceProbeView> services, List<ServiceProbeView> middlewares) {
        List<String> rows = new ArrayList<>();
        services.stream().filter(item -> "DOWN".equals(item.status()) && Boolean.TRUE.equals(item.required()))
                .forEach(item -> rows.add("关键服务未启动：" + item.name() + "（" + item.ownerModule() + "，端口 " + item.port() + "）"));
        middlewares.stream().filter(item -> "DOWN".equals(item.status()) && Boolean.TRUE.equals(item.required()))
                .forEach(item -> rows.add("关键中间件不可达：" + item.name() + "（" + item.endpoint() + "）"));
        boolean gatewayDown = services.stream().anyMatch(item -> "gateway".equals(item.code()) && "DOWN".equals(item.status()));
        if (gatewayDown) {
            rows.add("前端 /api/** 会先转发到 Gateway。Gateway 未启动时，前端会出现批量 500。请优先启动 GatewayApplication。");
        }
        boolean redisDown = middlewares.stream().anyMatch(item -> "redis".equals(item.code()) && "DOWN".equals(item.status()));
        if (redisDown) {
            rows.add("Redis 不可达会导致推荐缓存、会员权益、会话等接口降级或失败。请确认端口 6379 和密码 redis123456。");
        }
        if (rows.isEmpty()) {
            rows.add("核心服务和中间件端口均可连接。若接口仍返回 500，请查看对应业务服务控制台的第一段异常。");
        }
        return rows;
    }

    private List<String> powershellChecks() {
        return List.of(
                "netstat -ano | findstr \":18080\"",
                "netstat -ano | findstr \":18081\"",
                "netstat -ano | findstr \":18084\"",
                "netstat -ano | findstr \":18085\"",
                "netstat -ano | findstr \":18090\"",
                "netstat -ano | findstr \":18091\"",
                "docker ps --format \"table {{.Names}}\\t{{.Ports}}\\t{{.Status}}\""
        );
    }

    private int count(List<ServiceProbeView> rows, String status) {
        return (int) rows.stream().filter(item -> status.equals(item.status())).count();
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }

    private String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private int intValue(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private record Target(String code, String name, String category, String host, int port, String endpoint,
                          boolean required, String ownerModule, String startHint) {
    }
}
