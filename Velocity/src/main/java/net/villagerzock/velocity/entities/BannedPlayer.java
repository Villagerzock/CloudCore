package net.villagerzock.velocity.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "banned_players")
public class BannedPlayer {
    @Id
    @Getter
    @Setter
    private UUID uuid;

    @Getter
    @Setter
    @Column
    private String username;

    @Getter
    @Setter
    @Column(nullable = false, length = 300)
    private String reason;

    @Getter
    @Setter
    @Column(nullable = false)
    private Instant bannedAt;

    @Getter
    @Setter
    @Column
    private Instant expiresAt;
}
