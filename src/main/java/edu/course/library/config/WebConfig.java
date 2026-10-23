package edu.course.library.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 拦截器仅处理 /api/**；前端静态页面 /index.html 对未登录用户可访问。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final SessionAuthInterceptor auth;
    public WebConfig(SessionAuthInterceptor auth) { this.auth=auth; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auth).addPathPatterns("/api/**");
    }
}
