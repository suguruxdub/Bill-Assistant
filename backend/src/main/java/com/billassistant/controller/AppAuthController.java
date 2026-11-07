package com.billassistant.controller;

import com.billassistant.auth.AuthClient;
import com.billassistant.auth.UserContext;
import com.billassistant.dto.AppAuthResponse;
import com.billassistant.dto.AppRefreshRequest;
import com.billassistant.dto.AppTokenResponse;
import com.billassistant.dto.LoginRequest;
import com.billassistant.dto.RegisterRequest;
import com.billassistant.dto.UserProfile;
import com.billassistant.service.AuthService;
import com.billassistant.service.UserSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/auth")
public class AppAuthController {
    private final AuthService authService;
    private final UserSessionService sessionService;

    public AppAuthController(AuthService authService, UserSessionService sessionService) {
        this.authService = authService;
        this.sessionService = sessionService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AppAuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return AppAuthResponse.from(authService.register(request, AuthClient.APP));
    }

    @PostMapping("/login")
    public AppAuthResponse login(@Valid @RequestBody LoginRequest request) {
        return AppAuthResponse.from(authService.login(request, AuthClient.APP));
    }

    @PostMapping("/refresh")
    public AppTokenResponse refresh(@Valid @RequestBody AppRefreshRequest request) {
        return AppTokenResponse.from(sessionService.refresh(request.refreshToken(), AuthClient.APP));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody AppRefreshRequest request) {
        sessionService.revoke(request.refreshToken(), AuthClient.APP);
    }

    @GetMapping("/me")
    public UserProfile me() {
        return authService.profile(UserContext.requireUid());
    }
}
