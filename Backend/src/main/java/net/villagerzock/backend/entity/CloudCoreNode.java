package net.villagerzock.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cloudcore_nodes", indexes = {
        @Index(name = "idx_cloudcore_nodes_public_id", columnList = "public_id", unique = true),
        @Index(name = "idx_cloudcore_nodes_ip", columnList = "ip_address", unique = true)
})
public class CloudCoreNode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "ip_address", nullable = false, unique = true, length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private boolean linked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "linked_at")
    private Instant linkedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private UserAccount owner;

    protected CloudCoreNode() {
    }

    public CloudCoreNode(String name, String ipAddress) {
        this.name = name;
        this.ipAddress = ipAddress;
    }

    @PrePersist
    void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public void markLinked(Instant instant) {
        linked = true;
        linkedAt = instant;
    }

    public void setOwner(UserAccount owner) {
        this.owner = owner;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getName() {
        return name;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public boolean isLinked() {
        return linked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public UserAccount getOwner() {
        return owner;
    }
}
