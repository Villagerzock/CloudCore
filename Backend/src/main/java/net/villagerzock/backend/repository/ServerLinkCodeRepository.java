package net.villagerzock.backend.repository;

import jakarta.persistence.LockModeType;
import net.villagerzock.backend.entity.ServerLinkCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ServerLinkCodeRepository extends JpaRepository<ServerLinkCode, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select code from ServerLinkCode code
            join fetch code.node node
            where code.codeHash = :codeHash
              and code.consumedAt is null
              and code.expiresAt > :now
            """)
    Optional<ServerLinkCode> findActiveForUpdate(
            @Param("codeHash") String codeHash,
            @Param("now") Instant now);

    boolean existsByCodeHashAndConsumedAtIsNullAndExpiresAtAfter(String codeHash, Instant now);

    @Modifying
    @Query("""
            update ServerLinkCode code
            set code.consumedAt = :now
            where code.node.id = :nodeId
              and code.consumedAt is null
              and code.expiresAt > :now
            """)
    int consumeActiveCodesForNode(@Param("nodeId") long nodeId, @Param("now") Instant now);

    long deleteByExpiresAtBefore(Instant cutoff);
}
