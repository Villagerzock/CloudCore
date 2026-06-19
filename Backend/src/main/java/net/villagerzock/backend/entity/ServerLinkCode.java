package net.villagerzock.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "server_link_codes", indexes = {
        @Index(name = "idx_server_link_codes_hash", columnList = "code_hash", unique = true),
        @Index(name = "idx_server_link_codes_expiry", columnList = "expires_at"),
        @Index(name = "idx_server_link_codes_server", columnList = "server_id")
})
public class ServerLinkCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private CloudCoreServer server;

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected ServerLinkCode() {
    }

    public ServerLinkCode(CloudCoreServer server, String codeHash, Instant expiresAt) {
        this.server = server;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public CloudCoreServer getServer() {
        return server;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void consume(Instant instant) {
        consumedAt = instant;
    }
}
