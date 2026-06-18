package net.villagerzock.backend;

import net.villagerzock.backend.controller.ProxyController;
import net.villagerzock.backend.controller.ServerController;
import net.villagerzock.backend.controller.TemplateController;
import net.villagerzock.backend.service.MetricsService;
import net.villagerzock.backend.service.ServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiContractTests {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ServerService serverService = new ServerService();
        MetricsService metricsService = new MetricsService();
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ServerController(serverService, metricsService),
                new ProxyController(metricsService),
                new TemplateController(serverService))
                .build();
    }

    @Test
    void exposesRunningServers() throws Exception {
        mockMvc.perform(get("/api/servers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("lobby-1"));
    }

    @Test
    void exposesSingleServerById() throws Exception {
        mockMvc.perform(get("/api/servers/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("survival-1"));

        mockMvc.perform(get("/api/servers/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exposesFrontendCompatibleTemplateFields() throws Exception {
        mockMvc.perform(get("/api/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].server_software").value("paper"));
    }

    @Test
    void exposesProxyAndServerMetrics() throws Exception {
        mockMvc.perform(get("/api/proxy/metrics/player-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("01.06.2026"))
                .andExpect(jsonPath("$[0].value").isNumber());

        mockMvc.perform(get("/api/servers/1/metrics/network"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inbound").isNumber())
                .andExpect(jsonPath("$[0].outbound").isNumber());
    }
}
