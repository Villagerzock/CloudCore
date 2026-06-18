package net.villagerzock.backend.repository;

import jakarta.persistence.LockModeType;
import net.villagerzock.backend.entity.LinkCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface LinkCodeRepository extends JpaRepository<LinkCode, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select code from LinkCode code
            join fetch code.user
            where code.codeHash = :codeHash
              and code.consumedAt is null
              and code.expiresAt > :now
            """)
    Optional<LinkCode> findActiveForUpdate(
            @Param("codeHash") String codeHash,
            @Param("now") Instant now);

    boolean existsByCodeHashAndConsumedAtIsNullAndExpiresAtAfter(String codeHash, Instant now);

    @Modifying
    @Query("""
            update LinkCode code
            set code.consumedAt = :now
            where code.user.id = :userId
              and code.consumedAt is null
              and code.expiresAt > :now
            """)
    int consumeActiveCodesForUser(@Param("userId") Long userId, @Param("now") Instant now);

    long deleteByExpiresAtBefore(Instant cutoff);
}
