package com.billassistant;

import com.billassistant.auth.AuthCookieService;
import com.billassistant.support.SessionTestConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SessionTestConfiguration.class)
@Transactional
class AppAuthIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void appRegisterReturnsBearerTokensAndBearerTokenCanReadBills() throws Exception {
        var response = mockMvc.perform(post("/api/app/auth/register")
                        .header("X-Requested-With", "BillAssistant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mobile_user\",\"password\":\"test-password\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("mobile_user"))
                .andExpect(jsonPath("$.tokens.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.tokens.accessToken").isString())
                .andExpect(jsonPath("$.tokens.refreshToken").isString())
                .andExpect(cookie().doesNotExist(AuthCookieService.ACCESS_COOKIE))
                .andReturn();
        JsonNode body = objectMapper.readTree(response.getResponse().getContentAsString());
        String access = body.at("/tokens/accessToken").asText();
        assertFalse(access.isBlank());

        mockMvc.perform(get("/api/app/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("mobile_user"));
        mockMvc.perform(get("/api/bills").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void appRefreshRotatesTokensAndOldRefreshTokenGetsConflict() throws Exception {
        JsonNode first = register();
        String oldRefresh = first.at("/tokens/refreshToken").asText();

        var refreshed = mockMvc.perform(post("/api/app/auth/refresh")
                        .header("X-Requested-With", "BillAssistant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("refreshToken", oldRefresh).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        JsonNode next = objectMapper.readTree(refreshed.getResponse().getContentAsString());
        String nextAccess = next.at("/accessToken").asText();
        String nextRefresh = next.at("/refreshToken").asText();
        assertNotNull(nextAccess);
        assertNotNull(nextRefresh);

        mockMvc.perform(post("/api/app/auth/refresh")
                        .header("X-Requested-With", "BillAssistant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("refreshToken", oldRefresh).toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFRESH_CONFLICT"));
        mockMvc.perform(get("/api/app/auth/me").header("Authorization", "Bearer " + nextAccess))
                .andExpect(status().isOk());
    }

    @Test
    void appEndpointRejectsWebCookieAndInvalidBearerDoesNotFallBackToCookie() throws Exception {
        var web = mockMvc.perform(post("/api/auth/register")
                        .header("X-Requested-With", "BillAssistant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"web_user\",\"password\":\"test-password\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        Cookie access = web.getCookie(AuthCookieService.ACCESS_COOKIE);
        assertNotNull(access);

        mockMvc.perform(get("/api/app/auth/me").cookie(access))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bills").cookie(access).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCESS_TOKEN_INVALID"));
    }

    @Test
    void capacitorOriginCanPreflightBearerRequestsWithoutCookieCredentials() throws Exception {
        mockMvc.perform(options("/api/app/auth/login")
                        .header("Origin", "capacitor://localhost")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,authorization,x-requested-with"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "capacitor://localhost"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    private JsonNode register() throws Exception {
        var response = mockMvc.perform(post("/api/app/auth/register")
                        .header("X-Requested-With", "BillAssistant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mobile_refresh\",\"password\":\"test-password\"}"))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString());
    }
}

