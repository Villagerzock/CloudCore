package net.villagerzock.backend;

import net.villagerzock.backend.entity.CloudCoreServer;
import net.villagerzock.backend.entity.UserAccount;
import net.villagerzock.backend.repository.CloudCoreServerRepository;
import net.villagerzock.backend.repository.UserAccountRepository;
import net.villagerzock.backend.service.NodeHandshakeClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CloudCoreServerLinkFlowTests {
    @MockitoBean
    private NodeHandshakeClient handshakeClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private CloudCoreServerRepository servers;

    @Test
    void userRegistersServerAndMatchingIpLinksItWithOneTimeCode() throws Exception {
        when(handshakeClient.getServers(anyLong())).thenReturn(List.of());
        users.saveAndFlush(new UserAccount(
                "link-test-user",
                "link-test@example.com",
                passwordEncoder.encode("test-password")));

        mockMvc.perform(post("/api/cloudcore-servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"production-1","ipAddress":"203.0.113.42"}
                                """))
                .andExpect(status().isUnauthorized());

        String serverResponse = mockMvc.perform(post("/api/cloudcore-servers")
                        .with(httpBasic("link-test-user", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"production-1","ipAddress":"203.0.113.42"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.ipAddress").value("203.0.113.42"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID serverId = UUID.fromString(
                objectMapper.readTree(serverResponse).get("serverId").asText());

        mockMvc.perform(post("/api/cloudcore-servers")
                        .with(httpBasic("link-test-user", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"duplicate","ipAddress":"203.0.113.42"}
                                """))
                .andExpect(status().isConflict());

        String codeResponse = mockMvc.perform(post(
                                "/api/cloudcore-servers/{serverId}/link-code",
                                serverId)
                        .with(httpBasic("link-test-user", "test-password")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serverId").value(serverId.toString()))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("\\d{6}")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String code = objectMapper.readTree(codeResponse).get("code").asText();
        Instant expiresAt = Instant.parse(objectMapper.readTree(codeResponse).get("expiresAt").asText());
        assertThat(expiresAt).isAfter(Instant.now().plusSeconds(4 * 60));

        mockMvc.perform(post("/api/cloudcore-servers/link")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.99");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s"}
                                """.formatted(code)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/cloudcore-servers/link")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.42");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s"}
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverId").value(serverId.toString()))
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.linkedAt").exists());

        String nodesResponse = mockMvc.perform(get("/api/nodes")
                        .with(httpBasic("link-test-user", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serverId").value(serverId.toString()))
                .andExpect(jsonPath("$[0].name").value("production-1"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long nodeId = objectMapper.readTree(nodesResponse).get(0).get("id").asLong();

        mockMvc.perform(get("/api/servers")
                        .with(httpBasic("link-test-user", "test-password")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/servers")
                        .param("node", Long.toString(nodeId))
                        .with(httpBasic("link-test-user", "test-password")))
                .andExpect(status().isOk());

        users.saveAndFlush(new UserAccount(
                "other-user",
                "other@example.com",
                passwordEncoder.encode("other-password")));
        mockMvc.perform(get("/api/nodes")
                        .with(httpBasic("other-user", "other-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/servers")
                        .param("node", Long.toString(nodeId))
                        .with(httpBasic("other-user", "other-password")))
                .andExpect(status().isForbidden());

        CloudCoreServer server = servers.findById(serverId).orElseThrow();
        assertThat(server.isLinked()).isTrue();
        assertThat(server.getUser().getUsername()).isEqualTo("link-test-user");

        mockMvc.perform(post("/api/cloudcore-servers/{serverId}/link-code", serverId)
                        .with(httpBasic("link-test-user", "test-password")))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/cloudcore-servers/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s"}
                                """.formatted(code)))
                .andExpect(status().isBadRequest());
    }
}
