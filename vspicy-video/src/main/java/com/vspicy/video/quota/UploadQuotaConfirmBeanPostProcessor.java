package com.vspicy.video.quota;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.dto.VideoUploadQuotaConfirmCommand;
import com.vspicy.video.service.VideoUploadQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
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
 * 上传完成后自动确认配额。
 *
 * 设计目标：
 * 1. 不改现有 VideoUploadService 源码。
 * 2. 不依赖 Java 参数名。
 * 3. 不覆盖 application.yml。
 * 4. 只在 complete/finish/merge 成功后确认用量。
 */
@Component
public class UploadQuotaConfirmBeanPostProcessor implements BeanPostProcessor {
    private static final String CONFIRMED_ATTRIBUTE = "VSPICY_UPLOAD_QUOTA_CONFIRMED";

    private final VideoUploadQuotaService quotaService;
    private final boolean enabled;
    private final boolean strict;
    private final Map<Class<?>, Method[]> methodCache = new ConcurrentHashMap<>();

    public UploadQuotaConfirmBeanPostProcessor(
            VideoUploadQuotaService quotaService,
            @Value("${vspicy.video.upload.quota.auto-confirm.enabled:true}") boolean enabled,
            @Value("${vspicy.video.upload.quota.auto-confirm.strict:false}") boolean strict
    ) {
        this.quotaService = quotaService;
        this.enabled = enabled;
        this.strict = strict;
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
            if (!shouldConfirm(invocation.getMethod().getName())) {
                return invocation.proceed();
            }

            Object result = invocation.proceed();

            try {
                confirmOnce(invocation.getArguments(), result, invocation.getMethod().getName());
            } catch (Exception ex) {
                if (strict) {
                    if (ex instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new BizException("上传配额自动确认失败：" + ex.getMessage());
                }
                System.err.println("上传配额自动确认失败，不中断上传完成响应: method="
                        + invocation.getMethod().getName()
                        + ", error="
                        + ex.getMessage());
            }

            return result;
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

    private boolean shouldConfirm(String methodName) {
        if (methodName == null) {
            return false;
        }

        String name = methodName.toLowerCase(Locale.ROOT);

        boolean action = name.contains("complete")
                || name.contains("finish")
                || name.contains("merge")
                || name.contains("success")
                || name.contains("done");

        boolean target = name.contains("upload")
                || name.contains("task")
                || name.contains("chunk")
                || name.contains("part");

        return action && target;
    }

    private void confirmOnce(Object[] args, Object result, String methodName) {
        HttpServletRequest request = currentRequest();
        if (request != null && Boolean.TRUE.equals(request.getAttribute(CONFIRMED_ATTRIBUTE))) {
            return;
        }

        Long userId = firstNonNull(
                resolveUserIdFromRequest(),
                findNumberByName(args, "userId", "uid", "creatorId", "ownerId"),
                findNumberByName(new Object[]{result}, "userId", "uid", "creatorId", "ownerId"),
                1L
        );

        Long sizeMb = firstNonNull(
                resolveSizeMb(args),
                resolveSizeMb(new Object[]{result})
        );

        if (sizeMb == null || sizeMb <= 0) {
            System.out.println("上传配额自动确认：未识别到文件大小，跳过 method=" + methodName);
            return;
        }

        Long videoId = firstNonNull(
                findNumberByName(new Object[]{result}, "videoId", "id", "bizId", "mediaId"),
                findNumberByName(args, "videoId", "id", "bizId", "mediaId")
        );

        String fileName = firstNonNullString(
                findStringByName(new Object[]{result}, "fileName", "filename", "originalFilename", "name", "title"),
                findStringByName(args, "fileName", "filename", "originalFilename", "name", "title")
        );

        quotaService.confirm(new VideoUploadQuotaConfirmCommand(
                userId,
                videoId,
                fileName,
                sizeMb
        ), userId);

        if (request != null) {
            request.setAttribute(CONFIRMED_ATTRIBUTE, true);
        }

        System.out.println("上传配额自动确认完成: userId="
                + userId
                + ", videoId="
                + videoId
                + ", fileName="
                + fileName
                + ", sizeMb="
                + sizeMb
                + ", method="
                + methodName);
    }

    private Long resolveUserIdFromRequest() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }

        return parseLong(request.getHeader("X-User-Id"));
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

    private String findStringByName(Object[] args, String... names) {
        if (args == null) {
            return null;
        }

        for (Object arg : args) {
            String value = findStringInObject(arg, names);
            if (value != null && !value.isBlank()) {
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

    private Long findNumberInObject(Object object, String... names) {
        if (object == null) {
            return null;
        }

        if (object instanceof Number number && names.length == 0) {
            return number.longValue();
        }

        if (object instanceof Map<?, ?> map) {
            for (String name : names) {
                Long parsed = numberValue(map.get(name));
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
            Long fromGetter = readNumberGetter(object, name);
            if (fromGetter != null) {
                return fromGetter;
            }

            Long fromField = readNumberField(object, name);
            if (fromField != null) {
                return fromField;
            }
        }

        return null;
    }

    private String findStringInObject(Object object, String... names) {
        if (object == null) {
            return null;
        }

        if (object instanceof String text) {
            return text;
        }

        if (object instanceof Map<?, ?> map) {
            for (String name : names) {
                Object value = map.get(name);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
            return null;
        }

        Class<?> clazz = object.getClass();
        if (isSimpleType(clazz)) {
            return null;
        }

        for (String name : names) {
            String fromGetter = readStringGetter(object, name);
            if (fromGetter != null) {
                return fromGetter;
            }

            String fromField = readStringField(object, name);
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
            Long fromGetter = readNumberGetter(object, name);
            if (fromGetter != null) {
                return new SizeCandidate(name, fromGetter);
            }

            Long fromField = readNumberField(object, name);
            if (fromField != null) {
                return new SizeCandidate(name, fromField);
            }
        }

        return null;
    }

    private Long readNumberGetter(Object target, String propertyName) {
        Object value = readGetter(target, propertyName);
        return numberValue(value);
    }

    private String readStringGetter(Object target, String propertyName) {
        Object value = readGetter(target, propertyName);
        return value == null ? null : String.valueOf(value);
    }

    private Object readGetter(Object target, String propertyName) {
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
                return method.invoke(target);
            } catch (Exception ignored) {
                return null;
            }
        }

        return null;
    }

    private Long readNumberField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return numberValue(value);
    }

    private String readStringField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value == null ? null : String.valueOf(value);
    }

    private Object readField(Object target, String fieldName) {
        Class<?> clazz = target.getClass();

        while (clazz != null && clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
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

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }

        for (T value : values) {
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    private String firstNonNullString(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
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
