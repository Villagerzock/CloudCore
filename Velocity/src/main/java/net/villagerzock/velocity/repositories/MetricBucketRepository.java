package net.villagerzock.velocity.repositories;

import net.villagerzock.velocity.entities.MetricBucket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MetricBucketRepository extends JpaRepository<MetricBucket, Long> {
    Optional<MetricBucket> findByScopeAndMetricAndResolutionAndBucketStart(
            String scope,
            String metric,
            String resolution,
            Instant bucketStart);

    List<MetricBucket> findByScopeAndMetricAndResolutionAndBucketStartGreaterThanEqualOrderByBucketStart(
            String scope,
            String metric,
            String resolution,
            Instant from);

    void deleteByResolutionAndBucketStartBefore(String resolution, Instant cutoff);
}
