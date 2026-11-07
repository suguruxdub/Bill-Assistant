package com.billassistant.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @Min(1) long accessMaxAgeSeconds,
        @Min(60) long refreshMaxAgeSeconds,
        @Min(1) long refreshGraceSeconds,
        @NotBlank String jwtSecret,
        @NotBlank String issuer,
        @NotBlank String audience,
        boolean cookieSecure
) {
}
