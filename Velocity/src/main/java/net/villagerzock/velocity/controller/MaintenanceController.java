package net.villagerzock.velocity.controller;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.villagerzock.velocity.entities.MaintenancePlayer;
import net.villagerzock.velocity.dto.MaintenanceStatusDto;
import net.villagerzock.velocity.repositories.MaintenancePlayerRepo;
import net.villagerzock.velocity.service.MaintenanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {
    private final ProxyServer proxyServer;
    private final MaintenancePlayerRepo maintenancePlayerRepo;
    private final MaintenanceService maintenanceService;

    public MaintenanceController(ProxyServer proxyServer, MaintenancePlayerRepo maintenancePlayerRepo, MaintenanceService maintenanceService) {
        this.proxyServer = proxyServer;
        this.maintenancePlayerRepo = maintenancePlayerRepo;
        this.maintenanceService = maintenanceService;
    }

    @GetMapping("/status")
    public MaintenanceStatusDto getStatus() {
        return new MaintenanceStatusDto(
                maintenanceService.isActive(),
                maintenancePlayerRepo.findAll().stream()
                        .map(player -> new MaintenanceStatusDto.PlayerEntry(
                                player.getUuid(),
                                proxyServer.getPlayer(player.getUuid()).map(Player::getUsername).orElse(null)))
                        .toList());
    }

    @PostMapping("/on")
    public ResponseEntity<String> turnMaintenanceOn() {
        maintenanceService.turnOn();
        return ResponseEntity.ok("on");
    }
    @PostMapping("/off")
    public ResponseEntity<String> turnMaintenanceOff() {
        maintenanceService.turnOff();
        return ResponseEntity.ok("off");
    }
    public static final Pattern MC_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    @PostMapping("/")
    public ResponseEntity<String> addPlayerToMaintenance(@RequestParam(required = false) String playerName, @RequestParam(required = false) UUID playerUUID) {
        if ((playerName == null || !MC_NAME.matcher(playerName).matches()) && playerUUID == null) {
            return ResponseEntity.badRequest().build();
        }

        if (playerUUID != null) {
            maintenanceService.addPlayer(playerUUID);
            return ResponseEntity.ok("add " + playerUUID);
        }

        Optional<Player> playerOpt = proxyServer.getPlayer(playerName);
        if (playerOpt.isEmpty()) return ResponseEntity.notFound().build();

        maintenanceService.addPlayer(playerOpt.get());

        return ResponseEntity.ok("add " + playerOpt.get().getUsername());
    }

    @DeleteMapping
    public ResponseEntity<String> removePlayerFromMaintenance(@RequestParam(required = false) String playerName, @RequestParam(required = false) UUID playerUUID) {
        if ((playerName == null || !MC_NAME.matcher(playerName).matches()) && playerUUID == null) {
            return ResponseEntity.badRequest().build();
        }

        if (playerUUID != null) {
            maintenanceService.removePlayer(playerUUID);
            return ResponseEntity.ok("remove " + playerUUID);
        }

        Optional<Player> playerOpt = proxyServer.getPlayer(playerName);
        if (playerOpt.isEmpty()) return ResponseEntity.notFound().build();
        maintenanceService.removePlayer(playerOpt.get());
        return ResponseEntity.ok("remove " + playerOpt.get().getUsername());
    }
}
