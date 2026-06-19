package net.villagerzock.velocity.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "metric_buckets", indexes = {
        @Index(name = "idx_metric_bucket_query", columnList = "scope,metric,resolution,bucket_start")
}, uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_metric_bucket",
                columnNames = {"scope", "metric", "resolution", "bucket_start"})
})
public class MetricBucket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String scope;

    @Column(nullable = false, length = 20)
    private String metric;

    @Column(nullable = false, length = 10)
    private String resolution;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "player_max")
    private Integer playerMax;

    @Column(name = "inbound_bytes")
    private Long inboundBytes;

    @Column(name = "outbound_bytes")
    private Long outboundBytes;

    protected MetricBucket() {
    }

    public MetricBucket(String scope, String metric, String resolution, Instant bucketStart) {
        this.scope = scope;
        this.metric = metric;
        this.resolution = resolution;
        this.bucketStart = bucketStart;
    }

    public void recordPlayerMaximum(int players) {
        playerMax = playerMax == null ? players : Math.max(playerMax, players);
    }

    public void addNetwork(long inbound, long outbound) {
        inboundBytes = (inboundBytes == null ? 0 : inboundBytes) + Math.max(0, inbound);
        outboundBytes = (outboundBytes == null ? 0 : outboundBytes) + Math.max(0, outbound);
    }

    public Instant getBucketStart() {
        return bucketStart;
    }

    public Integer getPlayerMax() {
        return playerMax;
    }

    public Long getInboundBytes() {
        return inboundBytes;
    }

    public Long getOutboundBytes() {
        return outboundBytes;
    }
}
