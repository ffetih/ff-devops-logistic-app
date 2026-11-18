package com.siose.ff.logistics.caller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/** Everything outside Actuator needs a token, and the tenant comes from that token only. */
@SpringBootTest
@AutoConfigureMockMvc
class CallerEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void withoutATokenTheAnswerIs401() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTenantHeaderAloneIsNotEnough() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("X-Tenant-Id", "ff-eu-freight"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theTenantAndRolesComeFromTheToken() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .with(jwt().jwt(token -> token.claim("tenant_id", "ff-eu-freight")
                                        .claim("preferred_username", "nexus.planner"))
                                .authorities(new SimpleGrantedAuthority("ROLE_LOGISTICS_PLANNER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("ff-eu-freight"))
                .andExpect(jsonPath("$.roles[0]").value("LOGISTICS_PLANNER"))
                .andExpect(jsonPath("$.authSource").value("JWT"));
    }

    @Test
    void actuatorStaysOpenForTheProbes() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }
}
