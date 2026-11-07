package com.billassistant.service;

import com.billassistant.auth.AccessTokenService;
import com.billassistant.auth.AuthClient;
import com.billassistant.auth.TokenPair;
import com.billassistant.config.AuthProperties;
import com.billassistant.dao.UserMapper;
import com.billassistant.exception.ApiException;
import com.billassistant.session.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class UserSessionService {

    private static final Logger log = LoggerFactory.getLogger(UserSessionService.class);
    private final SessionStore sessionStore;
    private final UserMapper userMapper;
    private final AuthProperties properties;
    private final AccessTokenService accessTokens;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public UserSessionService(SessionStore sessionStore, UserMapper userMapper, AuthProperties properties,
                              AccessTokenService accessTokens, Clock clock) {
        this.sessionStore = sessionStore;
        this.userMapper = userMapper;
        this.properties = properties;
        this.accessTokens = accessTokens;
        this.clock = clock;
    }

    public TokenPair create(long uid) {
        return create(uid, AuthClient.WEB);
    }

    public TokenPair create(long uid, AuthClient client) {
        String sid = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = newRefreshToken(sid);
        long expiresAt = clock.instant().getEpochSecond() + properties.refreshMaxAgeSeconds();
        TokenPair pair = pair(uid, sid, refreshToken, expiresAt, client);
        removeIfTransactionRollsBack(sid);
        accessStore(() -> {
            sessionStore.save(sid, hash(refreshToken), uid, expiresAt, client);
            return null;
        });
        return pair;
    }

    public TokenPair refresh(String token) {
        return refresh(token, AuthClient.WEB);
    }

    public TokenPair refresh(String token, AuthClient client) {
        String sid = sessionId(token);
        if (sid == null) throw invalidRefresh();
        var session = accessStore(() -> sessionStore.find(sid));
        long now = clock.instant().getEpochSecond();
        if (session == null || session.expiresAt() <= now || session.client() != client) throw invalidRefresh();
        var user = userMapper.findById(session.uid());
        if (user == null || !user.isEnabled()) {
            accessStore(() -> { sessionStore.delete(sid); return null; });
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "账号不可用，请重新登录");
        }
        String nextToken = newRefreshToken(sid);
        TokenPair pair = pair(session.uid(), sid, nextToken, session.expiresAt(), client);
        var result = accessStore(() -> sessionStore.rotate(sid, hash(token), hash(nextToken), now,
                properties.refreshGraceSeconds()));
        return switch (result) {
            case SUCCESS -> pair;
            case CONFLICT -> throw new ApiException(HttpStatus.CONFLICT, "REFRESH_CONFLICT", "其他请求正在刷新登录态，请稍后重试");
            case REUSED -> throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_REUSED", "登录凭证已被重复使用，请重新登录");
            case INVALID -> throw invalidRefresh();
        };
    }

    public void revoke(String token) {
        revoke(token, AuthClient.WEB);
    }

    public void revoke(String token, AuthClient client) {
        String sid = sessionId(token);
        if (sid != null) {
            accessStore(() -> {
                var session = sessionStore.find(sid);
                if (session != null && session.client() == client) sessionStore.revoke(sid, hash(token));
                return null;
            });
        }
    }

    private TokenPair pair(long uid, String sid, String refreshToken, long expiresAt, AuthClient client) {
        long remaining = expiresAt - clock.instant().getEpochSecond();
        if (remaining <= 0) throw invalidRefresh();
        long accessAge = Math.min(properties.accessMaxAgeSeconds(), remaining);
        return new TokenPair(accessTokens.issue(uid, sid, accessAge, client), refreshToken, accessAge, remaining);
    }

    private String newRefreshToken(String sid) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return sid + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sessionId(String token) {
        return token != null && token.matches("[a-f0-9]{32}\\.[A-Za-z0-9_-]{43}") ? token.substring(0, 32) : null;
    }

    private ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "登录已失效，请重新登录");
    }

    private <T> T accessStore(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE", "登录服务暂时不可用，请稍后重试");
        }
    }

    private void removeIfTransactionRollsBack(String sid) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        sessionStore.delete(sid);
                    } catch (DataAccessException exception) {
                        log.warn("Could not clean up a rolled-back refresh session; TTL remains in effect");
                    }
                }
            }
        });
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
