package net.villagerzock.velocity.service;

import com.velocitypowered.api.proxy.Player;
import net.villagerzock.velocity.dto.BannedPlayerDto;
import net.villagerzock.velocity.dto.ResolvedBanRequestDto;
import net.villagerzock.velocity.dto.UpdateBanRequestDto;
import net.villagerzock.velocity.entities.BannedPlayer;
import net.villagerzock.velocity.repositories.BannedPlayerRepo;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class BanService {
    private final BannedPlayerRepo bans;

    public BanService(BannedPlayerRepo bans) {
        this.bans = bans;
    }

    public List<BannedPlayerDto> getActiveBans() {
        Instant now = Instant.now();
        return bans.findAll().stream()
                .filter(ban -> ban.getExpiresAt() == null || ban.getExpiresAt().isAfter(now))
                .sorted(Comparator.comparing(BannedPlayer::getBannedAt).reversed())
                .map(this::toDto)
                .toList();
    }

    public BannedPlayerDto createBan(ResolvedBanRequestDto request) {
        BannedPlayer ban = bans.findById(request.uuid()).orElseGet(BannedPlayer::new);
        ban.setUuid(request.uuid());
        ban.setUsername(normalizeUsername(request.name()));
        ban.setReason(normalizeReason(request.reason()));
        ban.setBannedAt(Instant.now());
        ban.setExpiresAt(requireFutureOrNull(request.expiresAt()));
        return toDto(bans.save(ban));
    }

    public BannedPlayerDto updateBan(UUID uuid, UpdateBanRequestDto request) {
        BannedPlayer ban = bans.findById(uuid)
                .orElseThrow(() -> new NoSuchElementException("Ban not found: " + uuid));
        if (request.reason() != null) {
            ban.setReason(normalizeReason(request.reason()));
        }
        ban.setExpiresAt(requireFutureOrNull(request.expiresAt()));
        return toDto(bans.save(ban));
    }

    public void deleteBan(UUID uuid) {
        if (!bans.existsById(uuid)) {
            throw new NoSuchElementException("Ban not found: " + uuid);
        }
        bans.deleteById(uuid);
    }

    public Optional<BannedPlayerDto> getActiveBan(Player player) {
        Optional<BannedPlayer> uuidBan = bans.findById(player.getUniqueId());
        Optional<BannedPlayer> usernameBan = normalizeUsername(player.getUsername()) == null
                ? Optional.empty()
                : bans.findByUsernameIgnoreCase(player.getUsername());

        return uuidBan.or(() -> usernameBan)
                .filter(ban -> ban.getExpiresAt() == null || ban.getExpiresAt().isAfter(Instant.now()))
                .map(ban -> {
                    if (ban.getUsername() == null || ban.getUsername().isBlank()) {
                        ban.setUsername(player.getUsername());
                        bans.save(ban);
                    }
                    return toDto(ban);
                });
    }

    private BannedPlayerDto toDto(BannedPlayer ban) {
        return new BannedPlayerDto(
                ban.getUuid(),
                ban.getUsername(),
                ban.getReason(),
                ban.getBannedAt(),
                ban.getExpiresAt());
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason is required");
        }
        return reason.trim();
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return username.trim();
    }

    private Instant requireFutureOrNull(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Ban expiration must be in the future");
        }
        return expiresAt;
    }
}
