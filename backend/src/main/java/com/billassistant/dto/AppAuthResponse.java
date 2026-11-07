package com.billassistant.dto;

import com.billassistant.service.AuthService;

public record AppAuthResponse(UserProfile user, AppTokenResponse tokens) {

    public static AppAuthResponse from(AuthService.AuthResult result) {
        return new AppAuthResponse(result.user(), AppTokenResponse.from(result.tokens()));
    }
}
