package net.villagerzock.velocity.repositories;

import net.villagerzock.velocity.entities.BannedPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BannedPlayerRepo extends JpaRepository<BannedPlayer, UUID> {
    Optional<BannedPlayer> findByUsernameIgnoreCase(String username);
}
