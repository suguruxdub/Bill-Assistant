package com.billassistant;

import com.billassistant.auth.AuthCookieService;
import com.billassistant.dto.RegisterRequest;
import com.billassistant.exception.ApiException;
import com.billassistant.service.AuthService;
import com.billassistant.service.UserSessionService;
import com.billassistant.support.SessionTestConfiguration;
import com.billassistant.support.SessionTestConfiguration.InMemorySessionStore;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SessionTestConfiguration.class)
class SessionFailureIntegrationTests {

    @Autowired private AuthService authService;
    @Autowired private UserSessionService sessionService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mockMvc;
    @Autowired private InMemorySessionStore sessionStore;

    @AfterEach
    void restoreStore() {
        sessionStore.setFailReads(false);
        sessionStore.setFailWrites(false);
    }

    @Test
    void databaseRollbackRemovesTheRedisSession() {
        var account = new TransactionTemplate(transactionManager).execute(status -> {
            var result = authService.register(request());
            assertEquals(1, sessionStore.size());
            status.setRollbackOnly();
            return result;
        });
        assertNotNull(account);
        assertEquals(0, sessionStore.size());
        assertNull(sessionStore.find(account.tokens().refreshToken().split("\\.")[0]));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
    }

    @Test
    void unavailableRedisRollsBackRegistration() {
        sessionStore.setFailWrites(true);
        var exception = assertThrows(ApiException.class, () -> authService.register(request()));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
        assertEquals(0, sessionStore.size());
    }

    @Test
    void outageReturns503WithoutClearingBrowserCookie() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var account = authService.register(request());
            try {
                sessionStore.setFailReads(true);
                mockMvc.perform(post("/api/auth/refresh").header("X-Requested-With", "BillAssistant")
                                .cookie(new Cookie(AuthCookieService.REFRESH_COOKIE, account.tokens().refreshToken())))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(header().doesNotExist("Set-Cookie"));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            } finally {
                status.setRollbackOnly();
            }
        });
        assertEquals(0, sessionStore.size());
    }

    private RegisterRequest request() {
        return new RegisterRequest("rollback_user", "test-password", null, null, null, null, null);
    }
}
