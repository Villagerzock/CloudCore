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
@Table(name = "cloudcore_instances", indexes = {
        @Index(name = "idx_cloudcore_instances_user", columnList = "user_id")
})
public class CloudCoreInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    protected CloudCoreInstance() {
    }

    public CloudCoreInstance(UserAccount user, String name, String ipAddress) {
        this.user = user;
        this.name = name;
        this.ipAddress = ipAddress;
    }

    @PrePersist
    void onCreate() {
        linkedAt = Instant.now();
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

    public Instant getLinkedAt() {
        return linkedAt;
    }
}
