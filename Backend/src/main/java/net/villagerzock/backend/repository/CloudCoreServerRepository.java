package net.villagerzock.backend.repository;

import jakarta.persistence.LockModeType;
import net.villagerzock.backend.entity.CloudCoreServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CloudCoreServerRepository extends JpaRepository<CloudCoreServer, UUID> {
    boolean existsByIpAddress(String ipAddress);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select server from CloudCoreServer server where server.id = :id")
    Optional<CloudCoreServer> findByIdForUpdate(@Param("id") UUID id);
}
