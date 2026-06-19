package net.villagerzock.backend.repository;

import net.villagerzock.backend.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    @Query("""
            select token from AuthToken token
            join fetch token.user
            where token.tokenHash = :tokenHash
              and token.expiresAt > :now
              and token.user.enabled = true
            """)
    Optional<AuthToken> findActiveByHash(
            @Param("tokenHash") String tokenHash,
            @Param("now") Instant now);

    long deleteByExpiresAtBefore(Instant cutoff);

    @Modifying
    @Query("delete from AuthToken token where token.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);
}
