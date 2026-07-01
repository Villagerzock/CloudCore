package net.villagerzock.velocity.repositories;

import net.villagerzock.velocity.entities.MaintenancePlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MaintenancePlayerRepo extends JpaRepository<MaintenancePlayer, UUID> {
    boolean existsByUsernameIgnoreCase(String username);
}
