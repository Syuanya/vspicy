package com.vspicy.video.config;

import com.vspicy.video.audit.OperationAuditAutoRecordInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnBean(OperationAuditAutoRecordInterceptor.class)
public class OperationAuditWebMvcConfig implements WebMvcConfigurer {
    private final OperationAuditAutoRecordInterceptor interceptor;

    public OperationAuditWebMvcConfig(OperationAuditAutoRecordInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/videos/**");
    }
}
