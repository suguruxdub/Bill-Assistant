package com.billassistant;

import com.billassistant.auth.AccessTokenService;
import com.billassistant.session.RedisSessionStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.billassistant.dto.CreateBillRequest;
import com.billassistant.dto.LoginRequest;
import com.billassistant.dto.RegisterRequest;
import com.billassistant.model.Bill;
import com.billassistant.service.BillService;
import com.billassistant.service.AuthService;
import com.billassistant.service.UserSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uses the configured MySQL and Redis; all test data is deleted after verification. */
@EnabledIfSystemProperty(named = "mysql.integration", matches = "true")
class MySqlPersistenceTests {

    @Test
    void billSurvivesApplicationRestart() throws Exception {
        Bill created;
        AuthService.AuthResult account;
        try (var first = startApplication()) {
            String username = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            account = first.getBean(AuthService.class).register(new RegisterRequest(username,
                    "integration-password", null, null, null, null, null));
            created = first.getBean(BillService.class).create(account.user().id(), new CreateBillRequest(
                    "persistence-test-" + UUID.randomUUID(), new BigDecimal("42.30"),
                    LocalDate.of(2026, 11, 7), "测试", true));
            assertTrue(created.id() > 0);
        }

        try (var restarted = startApplication()) {
            String loginToken = null;
            try {
                assertEquals(account.user().id(), restarted.getBean(AccessTokenService.class).verify(account.tokens().accessToken()).uid());
                var refreshed = restarted.getBean(UserSessionService.class).refresh(account.tokens().refreshToken());
                restarted.getBean(UserSessionService.class).revoke(refreshed.refreshToken());
                var login = restarted.getBean(AuthService.class).login(new LoginRequest(
                        account.user().username(), "integration-password"));
                loginToken = login.tokens().refreshToken();
                assertEquals(account.user().id(), login.user().id());
                Bill stored = restarted.getBean(BillService.class).findAll(account.user().id()).stream()
                        .filter(bill -> bill.id() == created.id())
                        .findFirst().orElseThrow();
                assertEquals(created, stored);
            } finally {
                restarted.getBean(UserSessionService.class).revoke(account.tokens().refreshToken());
                String originalRefresh = account.tokens().refreshToken();
                String usedKey = RedisSessionStore.key(originalRefresh.split("\\.")[0]) + ":used:"
                        + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(originalRefresh.getBytes(StandardCharsets.UTF_8)));
                restarted.getBean(StringRedisTemplate.class).delete(usedKey);
                if (loginToken != null) restarted.getBean(UserSessionService.class).revoke(loginToken);
                restarted.getBean(JdbcTemplate.class)
                        .update("DELETE FROM bills WHERE id = ?", created.id());
                restarted.getBean(JdbcTemplate.class)
                        .update("DELETE FROM users WHERE id = ?", account.user().id());
            }
        }
    }

    private ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(BillAssistantApplication.class)
                .web(WebApplicationType.NONE)
                .run();
    }
}
