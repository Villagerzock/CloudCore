package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.ChartPointDto;
import net.villagerzock.backend.dto.NetworkPointDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class MetricsService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int[] PLAYER_COUNTS = {
            153, 213, 198, 225, 241, 234, 256, 249, 271, 265,
            288, 302, 295, 317, 326, 321, 348, 362, 354, 381,
            397, 389, 425, 447, 438, 481, 516, 503, 578, 642
    };

    public List<ChartPointDto> getProxyPlayerCount() {
        return createPlayerCount(0);
    }

    public List<NetworkPointDto> getProxyNetwork() {
        return createNetwork(0);
    }

    public List<ChartPointDto> getServerPlayerCount(long serverId) {
        return createPlayerCount(serverId);
    }

    public List<NetworkPointDto> getServerNetwork(long serverId) {
        return createNetwork(serverId);
    }

    private List<ChartPointDto> createPlayerCount(long seed) {
        LocalDate firstDay = LocalDate.of(2026, 6, 1);
        int offset = Math.toIntExact(Math.floorMod(seed, 17));
        return IntStream.range(0, PLAYER_COUNTS.length)
                .mapToObj(index -> new ChartPointDto(
                        firstDay.plusDays(index).format(DATE_FORMAT),
                        PLAYER_COUNTS[index] + offset))
                .toList();
    }

    private List<NetworkPointDto> createNetwork(long seed) {
        List<ChartPointDto> players = createPlayerCount(seed);
        return IntStream.range(0, players.size())
                .mapToObj(index -> {
                    ChartPointDto point = players.get(index);
                    int inbound = (int) Math.round(point.value() * (1.8 + index % 4 * 0.15));
                    int outbound = (int) Math.round(point.value() * (0.9 + index % 3 * 0.12));
                    return new NetworkPointDto(point.key(), inbound, outbound);
                })
                .toList();
    }
}
