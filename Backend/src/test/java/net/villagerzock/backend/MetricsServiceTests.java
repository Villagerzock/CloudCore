package net.villagerzock.backend;

import net.villagerzock.backend.service.MetricsService;
import net.villagerzock.backend.service.NodeHandshakeClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsServiceTests {
    private final NodeHandshakeClient handshakeClient = mock(NodeHandshakeClient.class);
    private final MetricsService metricsService = new MetricsService(handshakeClient);

    @Test
    void returnsZeroPlayerCountMetricWhenNodeIsUnavailable() {
        when(handshakeClient.getProxyPlayerCount(1L, "days"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));

        assertThat(metricsService.getProxyPlayerCount(1L, "days"))
                .singleElement()
                .satisfies(point -> {
                    assertThat(point.key()).isNotBlank();
                    assertThat(point.value()).isZero();
                });
    }

    @Test
    void returnsZeroNetworkMetricWhenNodeIsUnavailable() {
        when(handshakeClient.getProxyNetwork(1L, "days"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));

        assertThat(metricsService.getProxyNetwork(1L, "days"))
                .singleElement()
                .satisfies(point -> {
                    assertThat(point.key()).isNotBlank();
                    assertThat(point.inbound()).isZero();
                    assertThat(point.outbound()).isZero();
                });
    }

    @Test
    void keepsValidationErrorsForInvalidMetricRange() {
        assertThatThrownBy(() -> metricsService.getProxyPlayerCount(1L, "years"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid metric range");
    }
}
