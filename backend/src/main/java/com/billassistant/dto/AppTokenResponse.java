package com.billassistant.dto;

import com.billassistant.auth.TokenPair;

/** Only App endpoints may return tokens in JSON. Web endpoints continue using HttpOnly cookies. */
public record AppTokenResponse(String tokenType, String accessToken, String refreshToken,
                               long expiresIn, long refreshExpiresIn) {

    public static AppTokenResponse from(TokenPair pair) {
        return new AppTokenResponse("Bearer", pair.accessToken(), pair.refreshToken(),
                pair.accessMaxAgeSeconds(), pair.refreshMaxAgeSeconds());
    }

    @Override
    public String toString() {
        return "AppTokenResponse[tokens=REDACTED]";
    }
}
