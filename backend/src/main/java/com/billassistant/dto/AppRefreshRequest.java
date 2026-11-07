package com.billassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AppRefreshRequest(
        @NotBlank @Size(max = 76) @Pattern(regexp = "[a-f0-9]{32}\\.[A-Za-z0-9_-]{43}") String refreshToken
) {
    @Override
    public String toString() {
        return "AppRefreshRequest[token=REDACTED]";
    }
}
