package net.villagerzock.corehandshake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;

import java.util.Objects;
import java.util.Map;

public final class CoreHandshakeInitializer {
    private CoreHandshakeInitializer() {
    }

    public static void start(CoreHandshakeProvider provider) {
        CoreHandshakeProvider requiredProvider = Objects.requireNonNull(provider, "provider");
        SpringApplication application = new SpringApplication(BootstrapConfiguration.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setApplicationContextFactory(ApplicationContextFactory.ofContextClass(
                AnnotationConfigServletWebServerApplicationContext.class));
        application.setDefaultProperties(Map.of("spring.output.ansi.enabled", "never"));
        application.addInitializers(context -> context.getBeanFactory()
                .registerSingleton("coreHandshakeProvider", requiredProvider));
        application.run();
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(CoreHandshakeAutoConfiguration.class)
    static class BootstrapConfiguration {
    }
}
