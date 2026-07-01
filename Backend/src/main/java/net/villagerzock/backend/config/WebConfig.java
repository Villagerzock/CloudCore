package net.villagerzock.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final NodeSelectionInterceptor nodeSelectionInterceptor;
    private final CorsProperties corsProperties;

    public WebConfig(NodeSelectionInterceptor nodeSelectionInterceptor, CorsProperties corsProperties) {
        this.nodeSelectionInterceptor = nodeSelectionInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(nodeSelectionInterceptor)
                .addPathPatterns(
                        "/api/servers/**",
                        "/api/templates/**",
                        "/api/proxy/**",
                        "/api/me",
                        "/api/users/**",
                        "/api/roles/**",
                        "/api/matchmaking/**",
                        "/api/maintenance/**",
                        "/api/bans/**",
                        "/api/metadata/**",
                        "/api/console/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(corsProperties.allowedOriginPatterns())
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .maxAge(3600);
    }
}
