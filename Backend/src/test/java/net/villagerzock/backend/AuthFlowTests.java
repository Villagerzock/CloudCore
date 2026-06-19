package net.villagerzock.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registrationAndLoginIssueUsableBearerTokens() throws Exception {
        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"auth-test-user",
                                  "email":"auth-test@example.com",
                                  "password":"test-password"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.username").value("auth-test-user"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String registrationToken = objectMapper.readTree(registerResponse).get("token").asText();
        mockMvc.perform(get("/api/servers"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/nodes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registrationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/cloudcore-servers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registrationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"auth-server","ipAddress":"198.51.100.77"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linked").value(false));

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registrationToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/servers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registrationToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"auth-test-user",
                                  "email":"different@example.com",
                                  "password":"test-password"
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"auth-test-user","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"auth-test-user","password":"test-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
