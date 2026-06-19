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
import java.util.UUID;

@Entity
@Table(name = "cloudcore_servers", indexes = {
        @Index(name = "idx_cloudcore_servers_user", columnList = "user_id"),
        @Index(name = "idx_cloudcore_servers_ip", columnList = "ip_address", unique = true)
})
public class CloudCoreServer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

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

    protected CloudCoreServer() {
    }

    public CloudCoreServer(UserAccount user, String name, String ipAddress) {
        this.user = user;
        this.name = name;
        this.ipAddress = ipAddress;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markLinked(Instant instant) {
        linked = true;
        linkedAt = instant;
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
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
}
