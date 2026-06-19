package net.villagerzock.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import net.villagerzock.velocity.service.ServerMangementService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:velocity_test;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CloudCoreVelocityPluginTests {
    @MockitoBean
    private ProxyServer proxyServer;

    @MockitoBean
    private ServerMangementService serverMangementService;

    @Test
    void contextLoads() {
    }

}
