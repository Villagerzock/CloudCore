package net.villagerzock.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MetricPushRequest(
        @NotBlank String console,
        @NotNull List<ChartPointDto> playerCount,
        @NotNull List<NetworkPointDto> network
) {
}
