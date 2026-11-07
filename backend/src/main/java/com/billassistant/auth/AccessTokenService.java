package com.billassistant.auth;

import com.billassistant.config.AuthProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/** Local JWT verification only: deliberately has no database or Redis dependency. */
@Service
public class AccessTokenService {

    public record Identity(long uid, String sessionId, AuthClient client) {
    }

    private final AuthProperties properties;
    private final Clock clock;
    private final byte[] secret;

    public AccessTokenService(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        try {
            this.secret = Base64.getDecoder().decode(properties.jwtSecret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("AUTH_JWT_SECRET must be Base64 encoded", exception);
        }
        if (secret.length < 32) {
            throw new IllegalStateException("AUTH_JWT_SECRET must contain at least 32 random bytes");
        }
        if (properties.accessMaxAgeSeconds() > properties.refreshMaxAgeSeconds()) {
            throw new IllegalStateException("Access lifetime cannot exceed refresh lifetime");
        }
    }

    public String issue(long uid, String sessionId, long maxAgeSeconds) {
        return issue(uid, sessionId, maxAgeSeconds, AuthClient.WEB);
    }

    public String issue(long uid, String sessionId, long maxAgeSeconds, AuthClient client) {
        Instant now = clock.instant();
        var claims = new JWTClaimsSet.Builder().subject(Long.toString(uid))
                .claim("sid", sessionId).claim("token_use", "access")
                .claim("client_type", client.value())
                .issuer(properties.issuer()).audience(properties.audience())
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(maxAgeSeconds)))
                .jwtID(UUID.randomUUID().toString()).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT).build(), claims);
        try {
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Could not sign access token", exception);
        }
    }

    public Identity verify(String token) {
        if (token == null || token.length() > 4096) return null;
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !JOSEObjectType.JWT.equals(jwt.getHeader().getType())
                    || jwt.getHeader().getCriticalParams() != null
                    || !jwt.verify(new MACVerifier(secret))) return null;
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Instant now = clock.instant();
            if (!properties.issuer().equals(claims.getIssuer())
                    || claims.getAudience() == null || !claims.getAudience().contains(properties.audience())
                    || !"access".equals(claims.getStringClaim("token_use"))
                    || claims.getExpirationTime() == null || !claims.getExpirationTime().toInstant().isAfter(now)
                    || claims.getIssueTime() == null || claims.getIssueTime().toInstant().isAfter(now)
                    || !claims.getExpirationTime().after(claims.getIssueTime())
                    || claims.getExpirationTime().toInstant().getEpochSecond()
                       - claims.getIssueTime().toInstant().getEpochSecond() > properties.accessMaxAgeSeconds()
                    || (claims.getNotBeforeTime() != null && claims.getNotBeforeTime().toInstant().isAfter(now))
                    || claims.getJWTID() == null) return null;
            long uid = Long.parseLong(claims.getSubject());
            String sid = claims.getStringClaim("sid");
            AuthClient client = AuthClient.fromStoredValue(claims.getStringClaim("client_type"));
            return uid > 0 && sid != null && sid.matches("[a-f0-9]{32}") ? new Identity(uid, sid, client) : null;
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            return null;
        }
    }
}
