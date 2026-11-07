package com.billassistant;

import com.billassistant.auth.AccessTokenService;
import com.billassistant.config.AuthProperties;
import com.billassistant.dto.RegisterRequest;
import com.billassistant.exception.ApiException;
import com.billassistant.service.AuthService;
import com.billassistant.service.UserSessionService;
import com.billassistant.session.RedisSessionStore;
import com.billassistant.session.SessionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfSystemProperty(named = "redis.integration", matches = "true")
class RedisSessionIntegrationTests {

    @Autowired private AuthService authService;
    @Autowired private UserSessionService sessionService;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private RedisSessionStore sessionStore;
    @Autowired private AccessTokenService accessTokens;
    @Autowired private AuthProperties properties;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void redisStoresRefreshHashAndAbsoluteTtlButNoAccessToken() throws Exception {
        var account = register();
        String refresh = account.tokens().refreshToken();
        String key = key(refresh);
        assertEquals(hash(refresh), redisTemplate.opsForHash().get(key, "current"));
        assertEquals(Long.toString(account.user().id()), redisTemplate.opsForHash().get(key, "uid"));
        Long ttl = redisTemplate.getExpire(key);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= properties.refreshMaxAgeSeconds());
        assertEquals(account.user().id(), accessTokens.verify(account.tokens().accessToken()).uid());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_sessions", Integer.class));
    }

    @Test
    void expiryPreventsRefreshButDoesNotInvalidateUnexpiredJwt() {
        var account = register();
        String key = key(account.tokens().refreshToken());
        redisTemplate.expire(key, Duration.ofMillis(20));
        await().atMost(Duration.ofSeconds(3)).until(() -> !Boolean.TRUE.equals(redisTemplate.hasKey(key)));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ApiException.class,
                () -> sessionService.refresh(account.tokens().refreshToken())).getStatus());
        assertNotNull(accessTokens.verify(account.tokens().accessToken()));
    }

    @Test
    void rotationIsAtomicAndDoesNotExtendAbsoluteExpiry() throws Exception {
        var account = register();
        String token = account.tokens().refreshToken();
        String sid = sid(token);
        long now = Instant.now().getEpochSecond();
        long expiry = sessionStore.find(sid).expiresAt();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> sessionStore.rotate(sid, hash(token), "new-hash-one", now, properties.refreshGraceSeconds()));
            var second = executor.submit(() -> sessionStore.rotate(sid, hash(token), "new-hash-two", now, properties.refreshGraceSeconds()));
            var outcomes = java.util.List.of(first.get(), second.get());
            assertTrue(outcomes.contains(SessionStore.Rotation.SUCCESS));
            assertTrue(outcomes.contains(SessionStore.Rotation.CONFLICT));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(expiry, sessionStore.find(sid).expiresAt());
        assertEquals(SessionStore.Rotation.REUSED, sessionStore.rotate(sid, hash(token), "unused",
                now + properties.refreshGraceSeconds() + 1, properties.refreshGraceSeconds()));
        assertNull(sessionStore.find(sid));
        redisTemplate.delete(key(token) + ":used:" + hash(token));
    }

    @Test
    void logoutRevokesOnlyThisBrowserAndCurrentPairStillWorksAfterConflict() throws Exception {
        var account = register();
        String token = account.tokens().refreshToken();
        var next = sessionService.refresh(token);
        try {
            assertEquals("REFRESH_CONFLICT", assertThrows(ApiException.class, () -> sessionService.refresh(token)).getCode());
            var other = sessionService.create(account.user().id());
            sessionService.revoke(next.refreshToken());
            assertNull(sessionStore.find(sid(next.refreshToken())));
            assertNotNull(sessionStore.find(sid(other.refreshToken())));
            sessionService.revoke(other.refreshToken());
        } finally {
            redisTemplate.delete(key(token) + ":used:" + hash(token));
        }
    }

    private AuthService.AuthResult register() {
        return authService.register(new RegisterRequest("redis_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                "integration-password", null, null, null, null, null));
    }
    private String sid(String token) { return token.split("\\.")[0]; }
    private String key(String token) { return RedisSessionStore.key(sid(token)); }
    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
