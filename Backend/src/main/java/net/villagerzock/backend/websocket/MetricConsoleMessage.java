package net.villagerzock.backend.websocket;

import net.villagerzock.backend.dto.ChartPointDto;
import net.villagerzock.backend.dto.NetworkPointDto;

import java.util.List;

public record MetricConsoleMessage(
        String type,
        String console,
        List<ChartPointDto> playerCount,
        List<NetworkPointDto> network
) {
    public MetricConsoleMessage(
            String console,
            List<ChartPointDto> playerCount,
            List<NetworkPointDto> network
    ) {
        this("metrics", console, playerCount, network);
    }
}
