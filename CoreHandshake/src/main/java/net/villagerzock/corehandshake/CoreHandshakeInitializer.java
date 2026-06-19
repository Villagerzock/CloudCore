package net.villagerzock.corehandshake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

import java.util.Objects;

public final class CoreHandshakeInitializer {
    private CoreHandshakeInitializer() {
    }

    public static ConfigurableApplicationContext start(CoreHandshakeProvider provider) {
        CoreHandshakeProvider requiredProvider = Objects.requireNonNull(provider, "provider");
        SpringApplication application = new SpringApplication(BootstrapConfiguration.class);
        application.addInitializers(context -> context.getBeanFactory()
                .registerSingleton("coreHandshakeProvider", requiredProvider));
        return application.run();
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(CoreHandshakeAutoConfiguration.class)
    static class BootstrapConfiguration {
    }
}
