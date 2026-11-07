package com.billassistant.controller;

import com.billassistant.auth.AuthCookieService;
import com.billassistant.auth.UserContext;
import com.billassistant.dto.LoginRequest;
import com.billassistant.dto.RegisterRequest;
import com.billassistant.dto.UserProfile;
import com.billassistant.service.AuthService;
import com.billassistant.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserSessionService sessionService;
    private final AuthCookieService cookieService;

    public AuthController(AuthService authService, UserSessionService sessionService, AuthCookieService cookieService) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.cookieService = cookieService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfile register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        var result = authService.register(request);
        cookieService.write(response, result.tokens());
        return result.user();
    }

    @PostMapping("/login")
    public UserProfile login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        var result = authService.login(request);
        cookieService.write(response, result.tokens());
        return result.user();
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refresh(HttpServletRequest request, HttpServletResponse response) {
        cookieService.write(response, sessionService.refresh(cookieService.readRefresh(request)));
    }

    @GetMapping("/me")
    public UserProfile me() {
        return authService.profile(UserContext.requireUid());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sessionService.revoke(cookieService.readRefresh(request));
        cookieService.clear(response);
    }
}
