package net.villagerzock.backend;

import net.villagerzock.backend.controller.ProxyController;
import net.villagerzock.backend.controller.NodeRoleController;
import net.villagerzock.backend.controller.ServerController;
import net.villagerzock.backend.controller.TemplateController;
import net.villagerzock.backend.dto.NodeRoleResponse;
import net.villagerzock.backend.security.NodePermission;
import net.villagerzock.backend.service.MetricsService;
import net.villagerzock.backend.service.NodeHandshakeClient;
import net.villagerzock.backend.service.NodePermissionService;
import net.villagerzock.backend.service.NodeRoleService;
import net.villagerzock.backend.service.ServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import net.villagerzock.backend.dto.ChartPointDto;
import net.villagerzock.backend.dto.NetworkPointDto;
import net.villagerzock.backend.dto.ServerDto;
import net.villagerzock.backend.dto.ServerTemplateDto;

class ApiContractTests {
    private MockMvc mockMvc;
    private NodePermissionService permissions;
    private NodeRoleService nodeRoleService;

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
        when(handshakeClient.getProxyPlayerCount(1, "days")).thenReturn(List.of(
                new ChartPointDto("01.06.2026", 153)));
        when(handshakeClient.getServerNetwork(1, "lobby-1")).thenReturn(List.of(
                new NetworkPointDto("01.06.2026", 250.0, 125.0)));
        ServerService serverService = new ServerService(handshakeClient);
        MetricsService metricsService = new MetricsService(handshakeClient);
        permissions = mock(NodePermissionService.class);
        nodeRoleService = mock(NodeRoleService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ServerController(serverService, metricsService, permissions),
                new ProxyController(metricsService),
                new TemplateController(serverService, permissions),
                new NodeRoleController(nodeRoleService, permissions))
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

        verifyNoInteractions(permissions);
    }

    @Test
    void createsRolesWithModifyPermission() throws Exception {
        when(nodeRoleService.createRole(1L, "Moderator", 24))
                .thenReturn(new NodeRoleResponse(3L, "Moderator", 24, Map.of(), Map.of(), 2L));

        mockMvc.perform(post("/api/roles")
                        .requestAttr("cloudcore.nodeId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Moderator","permissions":24}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Moderator"))
                .andExpect(jsonPath("$.permissions").value(24))
                .andExpect(jsonPath("$.previousRoleId").value(2));

        verify(permissions).require(nullable(Authentication.class), eq(1L), eq(NodePermission.ROLES_ADD));
    }
}
