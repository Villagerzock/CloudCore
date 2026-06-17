package net.villagerzock.velocity.controller;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.villagerzock.velocity.entities.MaintenancePlayer;
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

        Optional<Player> playerOpt = playerUUID == null ? proxyServer.getPlayer(playerName) : proxyServer.getPlayer(playerUUID);

        if (playerOpt.isEmpty()) return ResponseEntity.notFound().build();

        maintenanceService.addPlayer(playerOpt.get());

        return ResponseEntity.ok("add " + playerOpt.get().getUsername());
    }

    @DeleteMapping
    public ResponseEntity<String> removePlayerFromMaintenance(@RequestParam(required = false) String playerName, @RequestParam(required = false) UUID playerUUID) {
        if ((playerName == null || !MC_NAME.matcher(playerName).matches()) && playerUUID == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<Player> playerOpt = playerUUID == null ? proxyServer.getPlayer(playerName) : proxyServer.getPlayer(playerUUID);

        if (playerOpt.isEmpty()) return ResponseEntity.notFound().build();

        maintenanceService.removePlayer(playerOpt.get());

        return ResponseEntity.ok("remove " + playerOpt.get().getUsername());
    }
}
