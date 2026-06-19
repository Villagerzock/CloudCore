package net.villagerzock.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "cloudcore_nodes", indexes = {
        @Index(name = "idx_cloudcore_nodes_server", columnList = "server_id", unique = true)
})
public class CloudCoreNode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id", nullable = false, unique = true)
    private CloudCoreServer server;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CloudCoreNode() {
    }

    public CloudCoreNode(CloudCoreServer server) {
        this.server = server;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CloudCoreServer getServer() {
        return server;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
