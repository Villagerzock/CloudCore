package net.villagerzock.backend;

import net.villagerzock.backend.controller.ProxyController;
import net.villagerzock.backend.controller.ServerController;
import net.villagerzock.backend.controller.TemplateController;
import net.villagerzock.backend.service.MetricsService;
import net.villagerzock.backend.service.NodeHandshakeClient;
import net.villagerzock.backend.service.ServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import net.villagerzock.backend.dto.ChartPointDto;
import net.villagerzock.backend.dto.NetworkPointDto;
import net.villagerzock.backend.dto.ServerDto;
import net.villagerzock.backend.dto.ServerTemplateDto;

class ApiContractTests {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        NodeHandshakeClient handshakeClient = mock(NodeHandshakeClient.class);
        when(handshakeClient.getServers(1)).thenReturn(List.of(
                new ServerDto("lobby-1", "lobby", 15, 20),
                new ServerDto("survival-1", "survival", 42, 100)));
        when(handshakeClient.getServer(1, "survival-1")).thenReturn(
                new ServerDto("survival-1", "survival", 42, 100));
        when(handshakeClient.getTemplates(1)).thenReturn(List.of(
                new ServerTemplateDto("lobby", "paper", "1.21.11")));
        when(handshakeClient.getProxyPlayerCount(1)).thenReturn(List.of(
                new ChartPointDto("01.06.2026", 153)));
        when(handshakeClient.getServerNetwork(1, "lobby-1")).thenReturn(List.of(
                new NetworkPointDto("01.06.2026", 250, 125)));
        ServerService serverService = new ServerService(handshakeClient);
        MetricsService metricsService = new MetricsService(handshakeClient);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ServerController(serverService, metricsService),
                new ProxyController(metricsService),
                new TemplateController(serverService))
                .build();
    }

    @Test
    void exposesRunningServers() throws Exception {
        mockMvc.perform(get("/api/servers").requestAttr("cloudcore.nodeId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("lobby-1"));
    }

    @Test
    void exposesSingleServerByName() throws Exception {
        mockMvc.perform(get("/api/servers/survival-1").requestAttr("cloudcore.nodeId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("survival-1"));

    }

    @Test
    void exposesFrontendCompatibleTemplateFields() throws Exception {
        mockMvc.perform(get("/api/templates").requestAttr("cloudcore.nodeId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].server_software").value("paper"));
    }

    @Test
    void exposesProxyAndServerMetrics() throws Exception {
        mockMvc.perform(get("/api/proxy/metrics/player-count").requestAttr("cloudcore.nodeId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("01.06.2026"))
                .andExpect(jsonPath("$[0].value").isNumber());

        mockMvc.perform(get("/api/servers/lobby-1/metrics/network").requestAttr("cloudcore.nodeId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inbound").isNumber())
                .andExpect(jsonPath("$[0].outbound").isNumber());
    }
}
