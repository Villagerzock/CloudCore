package net.villagerzock.backend.repository;

import net.villagerzock.backend.entity.Username;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsernameRepository extends JpaRepository<Username, UUID> {
}
