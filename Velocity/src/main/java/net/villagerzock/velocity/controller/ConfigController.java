package net.villagerzock.velocity.controller;

import net.villagerzock.velocity.config.CloudCoreConfiguration;
import net.villagerzock.velocity.dto.ConfigDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConfigController {

    private final CloudCoreConfiguration cloudCoreConfiguration;

    public ConfigController(CloudCoreConfiguration cloudCoreConfiguration) {
        this.cloudCoreConfiguration = cloudCoreConfiguration;
    }

    @PostMapping("/configure")
    public ResponseEntity<String> configure(@RequestBody ConfigDto configDto) {
        if (configDto.lobbyServer() != null) {
            cloudCoreConfiguration.setLobbyServer(configDto.lobbyServer());
        }
        if (configDto.matchmakingServerConfigs() != null) {
            cloudCoreConfiguration.setMatchmakingServerConfigs(configDto.matchmakingServerConfigs());
        }
        if (configDto.maintenanceMotd() != null) {
            cloudCoreConfiguration.setMaintenanceMotd(configDto.maintenanceMotd());
        }
        if (configDto.banMessage() != null) {
            cloudCoreConfiguration.setBanMessage(configDto.banMessage());
        }

        return ResponseEntity.ok("Configured");
    }
}
