package net.villagerzock.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final NodeSelectionInterceptor nodeSelectionInterceptor;

    public WebConfig(NodeSelectionInterceptor nodeSelectionInterceptor) {
        this.nodeSelectionInterceptor = nodeSelectionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(nodeSelectionInterceptor)
                .addPathPatterns(
                        "/api/servers/**",
                        "/api/templates/**",
                        "/api/proxy/**",
                        "/api/metadata/**",
                        "/api/console/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "Authorization", "X-CloudCore-Server-Id");
    }
}
