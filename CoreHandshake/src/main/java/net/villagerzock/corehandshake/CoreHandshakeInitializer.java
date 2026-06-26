package net.villagerzock.corehandshake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;

import java.util.Objects;
import java.util.Map;

public final class CoreHandshakeInitializer {
    private CoreHandshakeInitializer() {
    }

    private static ConfigurableApplicationContext runningSpring = null;

    public static synchronized void start(CoreHandshakeProvider provider) {
        CoreHandshakeProvider requiredProvider = Objects.requireNonNull(provider, "provider");
        SpringApplication application = new SpringApplication(BootstrapConfiguration.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setApplicationContextFactory(ApplicationContextFactory.ofContextClass(
                AnnotationConfigServletWebServerApplicationContext.class));
        application.setDefaultProperties(Map.of(
                "spring.output.ansi.enabled", "never",
                "spring.main.register-shutdown-hook", "false"
        ));
        application.addInitializers(context -> context.getBeanFactory()
                .registerSingleton("coreHandshakeProvider", requiredProvider));
        runningSpring = application.run();
    }

    public static synchronized void stop() {
        ConfigurableApplicationContext context = runningSpring;
        runningSpring = null;
        if (context == null) {
            return;
        }

        Thread closer = Thread.ofPlatform()
                .name("core-handshake-spring-close")
                .daemon(true)
                .start(context::close);
        try {
            closer.join(10_000);
            if (closer.isAlive()) {
                System.err.println("Timed out while closing CoreHandshake Spring context.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(CoreHandshakeAutoConfiguration.class)
    static class BootstrapConfiguration {
    }
}
