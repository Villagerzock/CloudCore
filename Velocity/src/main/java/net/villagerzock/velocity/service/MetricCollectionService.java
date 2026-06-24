package net.villagerzock.velocity.service;

import com.velocitypowered.api.proxy.ProxyServer;
import net.villagerzock.velocity.dto.NetworkMetricPoint;
import net.villagerzock.velocity.dto.PlayerMetricPoint;
import net.villagerzock.velocity.entities.MetricBucket;
import net.villagerzock.velocity.repositories.MetricBucketRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MetricCollectionService {
    private static final String PROXY = "proxy";
    private static final String PLAYERS = "players";
    private static final String NETWORK = "network";
    private static final long MEBIBYTE = 1024L * 1024L;
    private static final Path RX_BYTES = Path.of("/sys/class/net/eth0/statistics/rx_bytes");
    private static final Path TX_BYTES = Path.of("/sys/class/net/eth0/statistics/tx_bytes");

    private final ProxyServer proxy;
    private final MetricBucketRepository buckets;
    private Long previousInbound;
    private Long previousOutbound;

    public MetricCollectionService(ProxyServer proxy, MetricBucketRepository buckets) {
        this.proxy = proxy;
        this.buckets = buckets;
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 0)
    @Transactional
    public void sample() {
        Instant now = Instant.now();
        recordPlayerCounts(now);
        sampleNetwork(now);
        buckets.deleteByResolutionAndBucketStartBefore("days", dayStart(now).minus(29, ChronoUnit.DAYS));
        buckets.deleteByResolutionAndBucketStartBefore("hours", hourStart(now).minus(23, ChronoUnit.HOURS));
        buckets.deleteByResolutionAndBucketStartBefore("minutes", minuteStart(now).minus(59, ChronoUnit.MINUTES));
    }

    @Transactional
    public void recordPlayerCounts() {
        recordPlayerCounts(Instant.now());
    }

    private void recordPlayerCounts(Instant now) {
        int proxyPlayers = proxy.getPlayerCount();
        recordPlayer(PROXY, "days", dayStart(now), proxyPlayers);
        recordPlayer(PROXY, "hours", hourStart(now), proxyPlayers);
        recordPlayer(PROXY, "minutes", minuteStart(now), proxyPlayers);

        proxy.getAllServers().forEach(server -> recordPlayer(
                server.getServerInfo().getName(),
                "minutes",
                minuteStart(now),
                server.getPlayersConnected().size()));
    }

    @Transactional(readOnly = true)
    public List<PlayerMetricPoint> playerMetrics(String scope, String resolution) {
        Instant from = switch (resolution) {
            case "days" -> dayStart(Instant.now()).minus(29, ChronoUnit.DAYS);
            case "hours" -> hourStart(Instant.now()).minus(23, ChronoUnit.HOURS);
            case "minutes" -> minuteStart(Instant.now()).minus(59, ChronoUnit.MINUTES);
            default -> throw new IllegalArgumentException("Unsupported player metric resolution");
        };
        return buckets.findByScopeAndMetricAndResolutionAndBucketStartGreaterThanEqualOrderByBucketStart(
                        scope, PLAYERS, resolution, from).stream()
                .map(bucket -> new PlayerMetricPoint(
                        bucket.getBucketStart().toString(),
                        bucket.getPlayerMax() == null ? 0 : bucket.getPlayerMax()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NetworkMetricPoint> networkMetrics(String resolution) {
        Instant from = switch (resolution) {
            case "days" -> dayStart(Instant.now()).minus(29, ChronoUnit.DAYS);
            case "minutes" -> minuteStart(Instant.now()).minus(59, ChronoUnit.MINUTES);
            default -> throw new IllegalArgumentException("Unsupported network metric resolution");
        };
        return buckets.findByScopeAndMetricAndResolutionAndBucketStartGreaterThanEqualOrderByBucketStart(
                        PROXY, NETWORK, resolution, from).stream()
                .map(bucket -> new NetworkMetricPoint(
                        bucket.getBucketStart().toString(),
                        bytesToMb(bucket.getInboundBytes()),
                        bytesToMb(bucket.getOutboundBytes())))
                .toList();
    }

    private void sampleNetwork(Instant now) {
        try {
            long inbound = Long.parseLong(Files.readString(RX_BYTES).trim());
            long outbound = Long.parseLong(Files.readString(TX_BYTES).trim());
            if (previousInbound != null && previousOutbound != null) {
                long inboundDelta = Math.max(0, inbound - previousInbound);
                long outboundDelta = Math.max(0, outbound - previousOutbound);
                recordNetwork("minutes", minuteStart(now), inboundDelta, outboundDelta);
                recordNetwork("days", dayStart(now), inboundDelta, outboundDelta);
            }
            previousInbound = inbound;
            previousOutbound = outbound;
        } catch (IOException | NumberFormatException ignored) {
            // Network counters are unavailable on unsupported platforms; player metrics still continue.
        }
    }

    private void recordPlayer(String scope, String resolution, Instant start, int players) {
        MetricBucket bucket = bucket(scope, PLAYERS, resolution, start);
        bucket.recordPlayerMaximum(players);
        buckets.save(bucket);
    }

    private void recordNetwork(String resolution, Instant start, long inbound, long outbound) {
        MetricBucket bucket = bucket(PROXY, NETWORK, resolution, start);
        bucket.addNetwork(inbound, outbound);
        buckets.save(bucket);
    }

    private MetricBucket bucket(String scope, String metric, String resolution, Instant start) {
        return buckets.findByScopeAndMetricAndResolutionAndBucketStart(scope, metric, resolution, start)
                .orElseGet(() -> new MetricBucket(scope, metric, resolution, start));
    }

    private Instant minuteStart(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MINUTES);
    }

    private Instant hourStart(Instant instant) {
        return instant.truncatedTo(ChronoUnit.HOURS);
    }

    private Instant dayStart(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private double bytesToMb(Long bytes) {
        return bytes == null ? 0 : Math.round(bytes * 100.0 / MEBIBYTE) / 100.0;
    }
}
