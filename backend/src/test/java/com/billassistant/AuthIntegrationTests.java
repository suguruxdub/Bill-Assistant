package com.billassistant;

import com.billassistant.auth.AccessTokenService;
import com.billassistant.auth.AuthCookieService;
import com.billassistant.auth.UserContext;
import com.billassistant.dto.UserProfile;
import com.billassistant.exception.ApiException;
import com.billassistant.support.SessionTestConfiguration;
import com.billassistant.support.SessionTestConfiguration.InMemorySessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SessionTestConfiguration.class)
@Transactional
class AuthIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private InMemorySessionStore sessionStore;
    @Autowired private AccessTokenService accessTokens;

    private record Account(Cookie access, Cookie refresh, UserProfile user) { }

    @Test
    void registrationStoresProfileAndIssuesTwoHttpOnlyCookies() throws Exception {
        Account account = register("Alice", "alice@example.com");
        assertEquals("alice", account.user().username());
        assertEquals("Alice", account.user().nickname());
        assertEquals("alice@example.com", account.user().email());
        assertEquals("13800138000", account.user().phone());
        assertEquals("FEMALE", account.user().gender());
        assertEquals("2000-01-02", account.user().birthday().toString());
        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, account.user().id());
        assertTrue(passwordEncoder.matches("test-password", hash));
        assertEquals(tokenHash(account.refresh()), sessionStore.currentHash(sid(account.refresh())));
        assertFalse(sessionStore.currentHash(sid(account.refresh())).contains(account.refresh().getValue()));
        assertEquals(account.user().id(), accessTokens.verify(account.access().getValue()).uid());
        assertTrue(account.access().isHttpOnly());
        assertTrue(account.refresh().isHttpOnly());
        assertEquals("Lax", account.access().getAttribute("SameSite"));
        assertEquals("/api", account.access().getPath());
        assertEquals("/api/auth", account.refresh().getPath());
        assertEquals(900, account.access().getMaxAge());
        assertEquals(2592000, account.refresh().getMaxAge());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_sessions", Integer.class));
        mockMvc.perform(get("/api/auth/me").cookie(account.access()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(account.user().id()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tokens").doesNotExist()).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void accessAuthenticationDoesNotReadRedisOrUserTable() throws Exception {
        Account account = register("alice", "alice@example.com");
        sessionStore.setFailReads(true);
        sessionStore.setFailWrites(true);
        try {
            // Even a disabled account's already-issued JWT can access bills until expiry.
            jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE id = ?", account.user().id());
            mockMvc.perform(get("/api/bills").cookie(account.access()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        } finally {
            sessionStore.setFailReads(false); sessionStore.setFailWrites(false);
        }
    }

    @Test
    void loginAndLogoutRevokeRefreshButCopiedJwtRemainsValidUntilExpiry() throws Exception {
        Account account = register("alice", "alice@example.com");
        var response = mockMvc.perform(post("/api/auth/login").header("X-Requested-With", "BillAssistant")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"ALICE\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie refresh = response.getCookie(AuthCookieService.REFRESH_COOKIE);
        Cookie access = response.getCookie(AuthCookieService.ACCESS_COOKIE);
        assertNotNull(refresh); assertNotNull(access);
        assertNotEquals(account.refresh().getValue(), refresh.getValue());
        // Logout deliberately works with no access cookie.
        mockMvc.perform(post("/api/auth/logout").cookie(refresh).header("X-Requested-With", "BillAssistant"))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge(AuthCookieService.ACCESS_COOKIE, 0))
                .andExpect(cookie().maxAge(AuthCookieService.REFRESH_COOKIE, 0));
        refresh(refresh, 401);
        mockMvc.perform(get("/api/bills").cookie(access)).andExpect(status().isOk());
        refresh(account.refresh(), 204); // Other browser is unaffected.
    }

    @Test
    void refreshRotatesBothCookiesWithoutExtendingAbsoluteDeadline() throws Exception {
        Account account = register("alice", "alice@example.com");
        long deadline = sessionStore.find(sid(account.refresh())).expiresAt();
        var response = mockMvc.perform(post("/api/auth/refresh").cookie(account.refresh()).header("X-Requested-With", "BillAssistant"))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        Cookie nextAccess = response.getCookie(AuthCookieService.ACCESS_COOKIE);
        Cookie nextRefresh = response.getCookie(AuthCookieService.REFRESH_COOKIE);
        assertNotNull(nextAccess); assertNotNull(nextRefresh);
        assertNotEquals(account.access().getValue(), nextAccess.getValue());
        assertNotEquals(account.refresh().getValue(), nextRefresh.getValue());
        assertEquals(deadline, sessionStore.find(sid(nextRefresh)).expiresAt());
        assertEquals(sid(account.refresh()), sid(nextRefresh));
        mockMvc.perform(get("/api/auth/me").cookie(nextAccess)).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh").cookie(account.refresh()).header("X-Requested-With", "BillAssistant"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFRESH_CONFLICT"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        sessionStore.ageUsedToken(sid(account.refresh()), tokenHash(account.refresh()));
        mockMvc.perform(post("/api/auth/refresh").cookie(account.refresh()).header("X-Requested-With", "BillAssistant"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_REUSED"));
        refresh(nextRefresh, 401);
    }

    @Test
    void refreshTokenCannotAccessBillsAndJwtCannotRefresh() throws Exception {
        Account account = register("alice", "alice@example.com");
        mockMvc.perform(get("/api/bills").cookie(account.refresh())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bills").cookie(new Cookie(AuthCookieService.ACCESS_COOKIE, account.refresh().getValue())))
                .andExpect(status().isUnauthorized());
        refresh(new Cookie(AuthCookieService.REFRESH_COOKIE, account.access().getValue()), 401);
        mockMvc.perform(get("/api/bills").header("X-User-Id", "1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCESS_TOKEN_INVALID"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void rejectsForgedAccessAndMissingRefresh() throws Exception {
        Account account = register("alice", "alice@example.com");
        String[] parts = account.access().getValue().split("\\.");
        parts[1] = parts[1].substring(0, 5) + "X" + parts[1].substring(6);
        mockMvc.perform(get("/api/bills").cookie(new Cookie(AuthCookieService.ACCESS_COOKIE, String.join(".", parts))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").header("X-Requested-With", "BillAssistant"))
                .andExpect(status().isUnauthorized());
        // An unknown secret within a known session must not revoke the real session.
        refresh(new Cookie(AuthCookieService.REFRESH_COOKIE, sid(account.refresh()) + "." + "a".repeat(43)), 401);
        refresh(account.refresh(), 204);
    }

    @Test
    void expiredAndDisabledRefreshSessionsAreRejected() throws Exception {
        Account expired = register("expired", "expired@example.com");
        sessionStore.expire(sid(expired.refresh()));
        refresh(expired.refresh(), 401);
        Account disabled = register("disabled", "disabled@example.com");
        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE id = ?", disabled.user().id());
        refresh(disabled.refresh(), 401);
        mockMvc.perform(get("/api/auth/me").cookie(disabled.access()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void rejectsDuplicateUsernameAndEmail() throws Exception {
        register("alice", "alice@example.com");
        for (String body : new String[]{"{\"username\":\"ALICE\",\"password\":\"test-password\"}",
                "{\"username\":\"other\",\"password\":\"test-password\",\"email\":\"ALICE@example.com\"}"}) {
            mockMvc.perform(post("/api/auth/register").header("X-Requested-With", "BillAssistant")
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        }
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
        assertEquals(1, sessionStore.size());
    }

    @Test
    void wrongPasswordAndUnknownUserReturnSameError() throws Exception {
        register("alice", "alice@example.com");
        for (String username : new String[]{"alice", "unknown"}) {
            mockMvc.perform(post("/api/auth/login").header("X-Requested-With", "BillAssistant")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(Map.of("username", username, "password", "wrong-password"))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("用户名或密码不正确"))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
    }

    @Test
    void mutationRequiresCsrfHeaderIncludingRefreshAndLogout() throws Exception {
        Account account = register("alice", "alice@example.com");
        for (String path : new String[]{"/api/auth/refresh", "/api/auth/logout", "/api/auth/login", "/api/bills"}) {
            mockMvc.perform(post(path).cookie(account.access(), account.refresh())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void billsAreIsolatedAndRequestCannotOverrideOwner() throws Exception {
        Account alice = register("alice", "alice@example.com");
        Account bob = register("bob", "bob@example.com");
        createBill(alice, "Alice bill", bob.user().id());
        createBill(bob, "Bob bill", alice.user().id());
        mockMvc.perform(get("/api/bills").cookie(alice.access()).param("uid", Long.toString(bob.user().id())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Alice bill"));
        mockMvc.perform(get("/api/bills").cookie(bob.access())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Bob bill"));
        assertEquals(alice.user().id(), jdbcTemplate.queryForObject("SELECT user_id FROM bills WHERE title = 'Alice bill'", Long.class));
        assertThrows(ApiException.class, UserContext::requireUid);
    }

    @Test
    void legacyBillsArePreservedButNotAssignedToNewUsers() throws Exception {
        jdbcTemplate.update("INSERT INTO bills (title, amount, billing_date, category, paid) VALUES ('legacy', 1, '2026-01-01', 'test', FALSE)");
        Account account = register("alice", "alice@example.com");
        mockMvc.perform(get("/api/bills").cookie(account.access())).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void validatesRegistrationAndBcryptByteLimit() throws Exception {
        for (var entry : Map.of("username", "invalid username", "email", "invalid-email",
                "password", "密".repeat(30), "birthday", "2999-01-01").entrySet()) {
            var body = objectMapper.createObjectNode().put("username", "validuser").put("password", "test-password");
            body.put(entry.getKey(), entry.getValue());
            mockMvc.perform(post("/api/auth/register").header("X-Requested-With", "BillAssistant")
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body)))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
    }

    private void refresh(Cookie refresh, int statusCode) throws Exception {
        mockMvc.perform(post("/api/auth/refresh").cookie(refresh).header("X-Requested-With", "BillAssistant"))
                .andExpect(status().is(statusCode));
    }

    private String sid(Cookie cookie) { return cookie.getValue().split("\\.")[0]; }

    private String tokenHash(Cookie cookie) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cookie.getValue().getBytes(StandardCharsets.UTF_8)));
    }

    private Account register(String username, String email) throws Exception {
        var body = objectMapper.createObjectNode().put("username", username).put("password", "test-password")
                .put("nickname", username).put("email", email).put("phone", "13800138000")
                .put("gender", "FEMALE").put("birthday", "2000-01-02");
        var response = mockMvc.perform(post("/api/auth/register").header("X-Requested-With", "BillAssistant")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tokens").doesNotExist()).andReturn().getResponse();
        return new Account(response.getCookie(AuthCookieService.ACCESS_COOKIE), response.getCookie(AuthCookieService.REFRESH_COOKIE),
                objectMapper.readValue(response.getContentAsByteArray(), UserProfile.class));
    }

    private void createBill(Account account, String title, long forgedUid) throws Exception {
        var body = objectMapper.createObjectNode().put("title", title).put("amount", 1)
                .put("billingDate", "2026-11-01").put("category", "test").put("paid", true)
                .put("userId", forgedUid).put("uid", forgedUid);
        mockMvc.perform(post("/api/bills").cookie(account.access()).header("X-Requested-With", "BillAssistant")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isCreated());
    }
}
