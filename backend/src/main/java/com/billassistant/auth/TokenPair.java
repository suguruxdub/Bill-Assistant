package com.billassistant.auth;

public record TokenPair(String accessToken, String refreshToken,
                        long accessMaxAgeSeconds, long refreshMaxAgeSeconds) {
}
