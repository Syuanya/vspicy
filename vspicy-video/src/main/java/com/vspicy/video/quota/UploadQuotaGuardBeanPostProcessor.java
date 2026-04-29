package com.vspicy.video.quota;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.dto.VideoUploadQuotaCheckResponse;
import com.vspicy.video.service.VideoUploadQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 上传初始化配额拦截器。
 *
 * 设计目标：
 * 1. 不改现有 VideoUploadService 源码。
 * 2. 不依赖 Java 参数名。
 * 3. 不覆盖 application.yml。
 * 4. 只在创建/初始化上传任务时校验，不影响 chunk 上传和 completeUpload。
 */
@Component
public class UploadQuotaGuardBeanPostProcessor implements BeanPostProcessor {
    private static final String CHECKED_ATTRIBUTE = "VSPICY_UPLOAD_QUOTA_CHECKED";

    private final VideoUploadQuotaService quotaService;
    private final boolean enabled;
    private final Map<Class<?>, Method[]> methodCache = new ConcurrentHashMap<>();

    public UploadQuotaGuardBeanPostProcessor(
            VideoUploadQuotaService quotaService,
            @Value("${vspicy.video.upload.quota.guard.enabled:true}") boolean enabled
    ) {
        this.quotaService = quotaService;
        this.enabled = enabled;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!enabled) {
            return bean;
        }

        if (!isVideoUploadService(bean, beanName)) {
            return bean;
        }

        ProxyFactory proxyFactory = new ProxyFactory(bean);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice((MethodInterceptor) invocation -> {
            if (!shouldCheck(invocation.getMethod().getName())) {
                return invocation.proceed();
            }

            Long sizeMb = resolveSizeMb(invocation.getArguments());
            if (sizeMb == null || sizeMb <= 0) {
                System.out.println("上传配额守卫：未识别到文件大小，跳过校验 method=" + invocation.getMethod().getName());
                return invocation.proceed();
            }

            Long userId = resolveUserId(invocation.getArguments());
            checkOnce(userId, sizeMb, invocation.getMethod().getName());
            return invocation.proceed();
        });

        return proxyFactory.getProxy();
    }

    private boolean isVideoUploadService(Object bean, String beanName) {
        if (bean == null) {
            return false;
        }

        String className = bean.getClass().getName();
        return "videoUploadService".equals(beanName)
                || className.equals("com.vspicy.video.service.VideoUploadService")
                || className.contains("com.vspicy.video.service.VideoUploadService");
    }

    private boolean shouldCheck(String methodName) {
        if (methodName == null) {
            return false;
        }

        String name = methodName.toLowerCase(Locale.ROOT);

        if (name.contains("complete") || name.contains("chunk") || name.contains("part") || name.contains("merge")) {
            return false;
        }

        boolean action = name.contains("init") || name.contains("create") || name.contains("start");
        boolean target = name.contains("upload") || name.contains("task");

        return action && target;
    }

    private void checkOnce(Long userId, Long sizeMb, String methodName) {
        HttpServletRequest request = currentRequest();

        if (request != null && Boolean.TRUE.equals(request.getAttribute(CHECKED_ATTRIBUTE))) {
            return;
        }

        VideoUploadQuotaCheckResponse response = quotaService.check(userId, sizeMb);
        if (!Boolean.TRUE.equals(response.allowed())) {
            throw new BizException(response.reason());
        }

        if (request != null) {
            request.setAttribute(CHECKED_ATTRIBUTE, true);
        }

        System.out.println("上传配额守卫：校验通过 userId="
                + userId
                + ", sizeMb="
                + sizeMb
                + ", method="
                + methodName
                + ", plan="
                + response.planCode());
    }

    private Long resolveUserId(Object[] args) {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            String header = request.getHeader("X-User-Id");
            Long fromHeader = parseLong(header);
            if (fromHeader != null) {
                return fromHeader;
            }
        }

        Long fromArgs = findNumberByName(args, "userId", "uid", "creatorId", "ownerId");
        return fromArgs == null ? 1L : fromArgs;
    }

    private Long resolveSizeMb(Object[] args) {
        Long explicitMb = findNumberByName(args, "sizeMb", "fileSizeMb", "totalSizeMb", "uploadSizeMb");
        if (explicitMb != null) {
            return Math.max(0L, explicitMb);
        }

        SizeCandidate candidate = findSizeCandidate(args);
        if (candidate == null || candidate.value() == null) {
            return null;
        }

        return toMb(candidate.value(), candidate.name());
    }

    private Long findNumberByName(Object[] args, String... names) {
        if (args == null) {
            return null;
        }

        for (Object arg : args) {
            Long value = findNumberInObject(arg, names);
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    private SizeCandidate findSizeCandidate(Object[] args) {
        if (args == null) {
            return null;
        }

        for (Object arg : args) {
            SizeCandidate value = findSizeInObject(arg);
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Long findNumberInObject(Object object, String... names) {
        if (object == null) {
            return null;
        }

        if (object instanceof Number number && names.length == 0) {
            return number.longValue();
        }

        if (object instanceof Map<?, ?> map) {
            for (String name : names) {
                Object value = map.get(name);
                Long parsed = numberValue(value);
                if (parsed != null) {
                    return parsed;
                }
            }
            return null;
        }

        Class<?> clazz = object.getClass();
        if (isSimpleType(clazz)) {
            return null;
        }

        for (String name : names) {
            Long fromGetter = readGetter(object, name);
            if (fromGetter != null) {
                return fromGetter;
            }

            Long fromField = readField(object, name);
            if (fromField != null) {
                return fromField;
            }
        }

        return null;
    }

    private SizeCandidate findSizeInObject(Object object) {
        if (object == null) {
            return null;
        }

        if (object instanceof Number number) {
            return new SizeCandidate("size", number.longValue());
        }

        if (object instanceof Map<?, ?> map) {
            String[] names = {
                    "sizeMb", "fileSizeMb", "totalSizeMb", "uploadSizeMb",
                    "fileSizeBytes", "totalSizeBytes", "contentLengthBytes",
                    "fileSize", "totalSize", "size", "contentLength"
            };

            for (String name : names) {
                Long value = numberValue(map.get(name));
                if (value != null) {
                    return new SizeCandidate(name, value);
                }
            }

            return null;
        }

        Class<?> clazz = object.getClass();
        if (isSimpleType(clazz)) {
            return null;
        }

        String[] names = {
                "sizeMb", "fileSizeMb", "totalSizeMb", "uploadSizeMb",
                "fileSizeBytes", "totalSizeBytes", "contentLengthBytes",
                "fileSize", "totalSize", "size", "contentLength"
        };

        for (String name : names) {
            Long fromGetter = readGetter(object, name);
            if (fromGetter != null) {
                return new SizeCandidate(name, fromGetter);
            }

            Long fromField = readField(object, name);
            if (fromField != null) {
                return new SizeCandidate(name, fromField);
            }
        }

        return null;
    }

    private Long readGetter(Object target, String propertyName) {
        String suffix = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        String[] getterNames = {"get" + suffix, "is" + suffix};

        Method[] methods = methodCache.computeIfAbsent(target.getClass(), Class::getMethods);
        for (Method method : methods) {
            if (method.getParameterCount() != 0) {
                continue;
            }

            String methodName = method.getName();
            if (!methodName.equals(getterNames[0]) && !methodName.equals(getterNames[1])) {
                continue;
            }

            try {
                return numberValue(method.invoke(target));
            } catch (Exception ignored) {
                return null;
            }
        }

        return null;
    }

    private Long readField(Object target, String fieldName) {
        Class<?> clazz = target.getClass();

        while (clazz != null && clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return numberValue(field.get(target));
            } catch (NoSuchFieldException ex) {
                clazz = clazz.getSuperclass();
            } catch (Exception ex) {
                return null;
            }
        }

        return null;
    }

    private Long numberValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof String text && !text.isBlank()) {
            return parseLong(text);
        }

        if (value instanceof BigDecimal decimal) {
            return decimal.longValue();
        }

        return null;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private Long toMb(Long value, String name) {
        if (value == null) {
            return null;
        }

        long safeValue = Math.max(0L, value);
        String safeName = name == null ? "" : name.toLowerCase(Locale.ROOT);

        if (safeName.contains("mb")) {
            return safeValue;
        }

        if (safeName.contains("byte") || safeValue > 10240L) {
            return Math.max(1L, (safeValue + 1024L * 1024L - 1L) / (1024L * 1024L));
        }

        return safeValue;
    }

    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || Number.class.isAssignableFrom(clazz)
                || CharSequence.class.isAssignableFrom(clazz)
                || Boolean.class == clazz
                || Character.class == clazz
                || clazz.getName().startsWith("java.time.")
                || clazz.getName().startsWith("java.util.");
    }

    private HttpServletRequest currentRequest() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                return attrs.getRequest();
            }
        } catch (Exception ignored) {
            // ignore
        }

        return null;
    }

    private record SizeCandidate(String name, Long value) {
    }
}
