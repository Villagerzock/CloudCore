package net.villagerzock.backend;

import net.villagerzock.backend.entity.CloudCoreInstance;
import net.villagerzock.backend.entity.UserAccount;
import net.villagerzock.backend.repository.CloudCoreInstanceRepository;
import net.villagerzock.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InstanceLinkFlowTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private CloudCoreInstanceRepository instances;

    @Test
    void authenticatedUserCanLinkOneInstanceWithOneTimeCode() throws Exception {
        users.saveAndFlush(new UserAccount(
                "link-test-user",
                "link-test@example.com",
                passwordEncoder.encode("test-password")));

        mockMvc.perform(post("/api/link-codes"))
                .andExpect(status().isUnauthorized());

        String codeResponse = mockMvc.perform(post("/api/link-codes")
                        .with(httpBasic("link-test-user", "test-password")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("\\d{6}")))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String code = objectMapper.readTree(codeResponse).get("code").asText();
        Instant expiresAt = Instant.parse(objectMapper.readTree(codeResponse).get("expiresAt").asText());
        assertThat(expiresAt).isAfter(Instant.now().plusSeconds(4 * 60));

        String linkResponse = mockMvc.perform(post("/api/instances/link")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.42");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"production-1"}
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instanceId").exists())
                .andExpect(jsonPath("$.name").value("production-1"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID instanceId = UUID.fromString(
                objectMapper.readTree(linkResponse).get("instanceId").asText());
        CloudCoreInstance instance = instances.findById(instanceId).orElseThrow();
        assertThat(instance.getUser().getUsername()).isEqualTo("link-test-user");
        assertThat(instance.getIpAddress()).isEqualTo("203.0.113.42");

        mockMvc.perform(post("/api/instances/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"replay-attempt"}
                                """.formatted(code)))
                .andExpect(status().isBadRequest());
    }
}
