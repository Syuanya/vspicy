package com.vspicy.admin.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminOperationAuditWebMvcConfig implements WebMvcConfigurer {
    private final AdminOperationAuditInterceptor interceptor;

    public AdminOperationAuditWebMvcConfig(AdminOperationAuditInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/admin/**");
    }
}
