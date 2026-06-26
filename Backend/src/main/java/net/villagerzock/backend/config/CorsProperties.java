package net.villagerzock.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CorsProperties {
    private final String[] allowedOriginPatterns;

    public CorsProperties(
            @Value("${cloudcore.cors.allowed-origin-patterns:http://*:* ,https://*:*}") String allowedOriginPatterns
    ) {
        this.allowedOriginPatterns = java.util.Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isBlank())
                .toArray(String[]::new);
    }

    public String[] allowedOriginPatterns() {
        return allowedOriginPatterns;
    }
}
