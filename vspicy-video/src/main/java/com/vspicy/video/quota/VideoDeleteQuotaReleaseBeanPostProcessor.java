package com.vspicy.video.quota;

import com.vspicy.common.exception.BizException;
import com.vspicy.video.dto.VideoUploadQuotaReleaseCommand;
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
 * 视频删除后自动释放上传配额。
 *
 * 设计目标：
 * 1. 不改现有删除接口源码。
 * 2. 不覆盖 application.yml。
 * 3. 删除成功后释放 quota。
 * 4. 默认释放失败不影响删除响应。
 */
@Component
public class VideoDeleteQuotaReleaseBeanPostProcessor implements BeanPostProcessor {
    private static final String RELEASED_ATTRIBUTE = "VSPICY_VIDEO_DELETE_QUOTA_RELEASED";

    private final VideoUploadQuotaService quotaService;
    private final boolean enabled;
    private final boolean strict;
    private final Map<Class<?>, Method[]> methodCache = new ConcurrentHashMap<>();

    public VideoDeleteQuotaReleaseBeanPostProcessor(
            VideoUploadQuotaService quotaService,
            @Value("${vspicy.video.upload.quota.auto-release.enabled:true}") boolean enabled,
            @Value("${vspicy.video.upload.quota.auto-release.strict:false}") boolean strict
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

        if (!isCandidateVideoService(bean, beanName)) {
            return bean;
        }

        ProxyFactory proxyFactory = new ProxyFactory(bean);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice((MethodInterceptor) invocation -> {
            if (!isDeleteMethod(invocation.getMethod().getName())) {
                return invocation.proceed();
            }

            Object result = invocation.proceed();

            try {
                releaseOnce(invocation.getArguments(), result, invocation.getMethod().getName());
            } catch (Exception ex) {
                if (strict) {
                    if (ex instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new BizException("视频删除配额自动释放失败：" + ex.getMessage());
                }
                System.err.println("视频删除配额自动释放失败，不中断删除响应: method="
                        + invocation.getMethod().getName()
                        + ", error="
                        + ex.getMessage());
            }

            return result;
        });

        return proxyFactory.getProxy();
    }

    private boolean isCandidateVideoService(Object bean, String beanName) {
        if (bean == null) {
            return false;
        }

        String className = bean.getClass().getName();
        String lowerBeanName = beanName == null ? "" : beanName.toLowerCase(Locale.ROOT);

        if (className.contains("VideoUploadQuotaService")) {
            return false;
        }

        if (!className.contains("com.vspicy.video.service")) {
            return false;
        }

        return lowerBeanName.contains("video")
                || lowerBeanName.contains("media")
                || className.toLowerCase(Locale.ROOT).contains("video")
                || className.toLowerCase(Locale.ROOT).contains("media");
    }

    private boolean isDeleteMethod(String methodName) {
        if (methodName == null) {
            return false;
        }

        String name = methodName.toLowerCase(Locale.ROOT);

        if (!(name.contains("delete") || name.contains("remove"))) {
            return false;
        }

        if (name.contains("quota") || name.contains("record") || name.contains("log")) {
            return false;
        }

        return true;
    }

    private void releaseOnce(Object[] args, Object result, String methodName) {
        HttpServletRequest request = currentRequest();
        if (request != null && Boolean.TRUE.equals(request.getAttribute(RELEASED_ATTRIBUTE))) {
            return;
        }

        Long videoId = firstNonNull(
                findNumberByName(args, "videoId", "id", "bizId", "mediaId"),
                findNumberByName(new Object[]{result}, "videoId", "id", "bizId", "mediaId")
        );

        if (videoId == null || videoId <= 0) {
            System.out.println("视频删除配额释放：未识别到 videoId，跳过 method=" + methodName);
            return;
        }

        Long userId = firstNonNull(
                resolveUserIdFromRequest(),
                findNumberByName(args, "userId", "uid", "creatorId", "ownerId"),
                findNumberByName(new Object[]{result}, "userId", "uid", "creatorId", "ownerId"),
                1L
        );

        quotaService.release(new VideoUploadQuotaReleaseCommand(
                null,
                videoId,
                "DELETE_VIDEO_AUTO"
        ), userId);

        if (request != null) {
            request.setAttribute(RELEASED_ATTRIBUTE, true);
        }

        System.out.println("视频删除配额释放完成: userId="
                + userId
                + ", videoId="
                + videoId
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

    private Long findNumberInObject(Object object, String... names) {
        if (object == null) {
            return null;
        }

        if (object instanceof Number number && names.length == 0) {
            return number.longValue();
        }

        if (object instanceof Number number && names.length > 0) {
            // 删除方法常见签名 deleteById(Long id)，这种情况下数字参数通常就是 videoId。
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

    private Long readNumberGetter(Object target, String propertyName) {
        Object value = readGetter(target, propertyName);
        return numberValue(value);
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
}
