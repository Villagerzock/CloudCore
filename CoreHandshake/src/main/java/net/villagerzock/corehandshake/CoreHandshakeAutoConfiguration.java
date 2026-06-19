package net.villagerzock.corehandshake;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({CoreHandshakeController.class, CoreHandshakeSecurityConfiguration.class})
public class CoreHandshakeAutoConfiguration {
}
