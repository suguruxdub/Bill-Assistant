package com.billassistant;

import com.billassistant.auth.AccessTokenService;
import com.billassistant.config.AuthProperties;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class AccessTokenServiceTests {
    private static final byte[] KEY = "test-only-secret-at-least-32-bytes-long-for-hmac-signing-1234567890".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");
    private static final String SID = "a".repeat(32);

    private AuthProperties properties(String issuer, String audience) {
        return new AuthProperties(900, 2592000, 5, Base64.getEncoder().encodeToString(KEY), issuer, audience, false);
    }

    private AccessTokenService service(Instant now) {
        return new AccessTokenService(properties("bill-assistant", "bill-assistant-web"), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void verifiesUidAndRejectsExpiredJwtWithoutAnyExternalStore() {
        String token = service(NOW).issue(42, SID, 900);
        assertEquals(42, service(NOW).verify(token).uid());
        assertEquals(SID, service(NOW).verify(token).sessionId());
        assertNotNull(service(NOW.plusSeconds(899)).verify(token));
        assertNull(service(NOW.plusSeconds(900)).verify(token));
        assertNull(service(NOW.minusSeconds(1)).verify(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "subject", "sid", "use", "expiry", "future", "no-expiry", "no-iat", "no-jti", "nbf", "long-lifetime", "algorithm", "key"})
    void rejectsInvalidClaimsAlgorithmsAndSignatures(String defect) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject(defect.equals("subject") ? "0" : "42")
                .issuer(defect.equals("issuer") ? "other" : "bill-assistant")
                .audience(defect.equals("audience") ? "other" : "bill-assistant-web")
                .claim("sid", defect.equals("sid") ? "invalid" : SID)
                .claim("token_use", defect.equals("use") ? "refresh" : "access");
        if (!defect.equals("no-iat")) claims.issueTime(Date.from(defect.equals("future") ? NOW.plusSeconds(1) : NOW));
        if (!defect.equals("no-jti")) claims.jwtID("test-jti");
        if (!defect.equals("no-expiry")) claims.expirationTime(Date.from(defect.equals("expiry") ? NOW : NOW.plusSeconds(defect.equals("long-lifetime") ? 901 : 900)));
        if (defect.equals("nbf")) claims.notBeforeTime(Date.from(NOW.plusSeconds(60)));
        JWSAlgorithm algorithm = defect.equals("algorithm") ? JWSAlgorithm.HS384 : JWSAlgorithm.HS256;
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build(), claims.build());
        byte[] signingKey = defect.equals("key") ? "wrong-key-with-at-least-thirty-two-bytes-of-entropy".getBytes(java.nio.charset.StandardCharsets.UTF_8) : KEY;
        jwt.sign(new MACSigner(signingKey));
        assertNull(service(NOW).verify(jwt.serialize()));
    }

    @Test
    void rejectsMalformedUnsignedAndOversizedTokens() {
        assertNull(service(NOW).verify(null));
        assertNull(service(NOW).verify("not-a-token"));
        assertNull(service(NOW).verify("x".repeat(5000)));
        assertNull(service(NOW).verify("eyJhbGciOiJub25lIn0.eyJzdWIiOiI0MiJ9."));
    }

    @Test
    void refusesWeakOrMissingSigningConfiguration() {
        assertThrows(IllegalStateException.class, () -> new AccessTokenService(
                new AuthProperties(900, 2592000, 5, "short", "issuer", "audience", false), Clock.systemUTC()));
    }
}
