package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.ServerDto;
import net.villagerzock.backend.dto.ServerTemplateDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ServerService {
    public List<ServerDto> getRunningServers() {
        return List.of(
                new ServerDto(1, "lobby-1", "lobby", 15, 20),
                new ServerDto(2, "survival-1", "survival", 42, 100)
        );
    }

    public Optional<ServerDto> getServerById(long id) {
        return getRunningServers().stream()
                .filter(server -> server.id() == id)
                .findFirst();
    }

    public List<ServerTemplateDto> getTemplates() {
        return List.of(
                new ServerTemplateDto(1, "lobby", "paper", "1.21.11"),
                new ServerTemplateDto(2, "survival", "paper", "1.21.11")
        );
    }
}
