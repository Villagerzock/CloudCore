package net.villagerzock.corehandshake;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class CoreHandshakeSecurityConfiguration {
    @Bean
    SecurityFilterChain coreHandshakeSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/core-handshake/v1/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
