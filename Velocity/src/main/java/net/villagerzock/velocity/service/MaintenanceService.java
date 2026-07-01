package net.villagerzock.velocity.service;

import com.velocitypowered.api.proxy.Player;
import io.swagger.v3.oas.annotations.servers.Server;
import net.villagerzock.velocity.entities.MaintenancePlayer;
import net.villagerzock.velocity.repositories.MaintenancePlayerRepo;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Service
public class MaintenanceService {
    private static final Path FILE = Path.of("maintenance.state");
    private static boolean isActive = false;
    private final MaintenancePlayerRepo maintenancePlayerRepo;

    public MaintenanceService(MaintenancePlayerRepo maintenancePlayerRepo) {
        this.maintenancePlayerRepo = maintenancePlayerRepo;
        load();
    }

    public void turnOn() {
        isActive = true;
        save();
    }

    public void turnOff() {
        isActive = false;
        save();
    }

    public boolean isActive() {
        return isActive;
    }

    private void load() {
        try {
            if (Files.exists(FILE)) {
                isActive = Boolean.parseBoolean(Files.readString(FILE).trim());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void save() {
        try {
            Files.writeString(FILE, Boolean.toString(isActive));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void addPlayer(Player player){
        addPlayer(player.getUniqueId(), player.getUsername());
    }

    public void addPlayer(UUID uuid){
        addPlayer(uuid, null);
    }

    public void addPlayer(UUID uuid, String username){
        MaintenancePlayer maintenancePlayer = new MaintenancePlayer();
        maintenancePlayer.setUuid(uuid);
        maintenancePlayer.setUsername(normalizeUsername(username));
        maintenancePlayer.setAddedOn(Instant.now());

        maintenancePlayerRepo.save(maintenancePlayer);
    }
    public void removePlayer(Player player){
        removePlayer(player.getUniqueId());
    }

    public void removePlayer(UUID uuid){
        maintenancePlayerRepo.deleteById(uuid);
    }

    public boolean hasPlayer(Player player){
        return maintenancePlayerRepo.existsById(player.getUniqueId());
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return username.trim();
    }
}
