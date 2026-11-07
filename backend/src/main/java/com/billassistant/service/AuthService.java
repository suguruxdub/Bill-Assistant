package com.billassistant.service;

import com.billassistant.auth.TokenPair;
import com.billassistant.auth.AuthClient;
import com.billassistant.dao.UserMapper;
import com.billassistant.dto.LoginRequest;
import com.billassistant.dto.RegisterRequest;
import com.billassistant.dto.UserProfile;
import com.billassistant.exception.ApiException;
import com.billassistant.model.UserEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AuthService {

    public record AuthResult(UserProfile user, TokenPair tokens) {
    }

    private final UserMapper userMapper;
    private final UserSessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserMapper userMapper, UserSessionService sessionService,
                       PasswordEncoder passwordEncoder, Clock clock) {
        this.userMapper = userMapper;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("unused-password-for-timing");
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        return register(request, AuthClient.WEB);
    }

    @Transactional
    public AuthResult register(RegisterRequest request, AuthClient client) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码不能超过 72 个 UTF-8 字节");
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.username().toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        String nickname = optionalText(request.nickname());
        user.setNickname(nickname == null ? user.getUsername() : nickname);
        String email = optionalText(request.email());
        user.setEmail(email == null ? null : email.toLowerCase(Locale.ROOT));
        user.setPhone(optionalText(request.phone()));
        user.setGender(request.gender() == null ? "UNSPECIFIED" : request.gender());
        user.setBirthday(request.birthday());
        user.setEnabled(true);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "用户名或邮箱已被使用");
        }
        return new AuthResult(UserProfile.from(user), sessionService.create(user.getId(), client));
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        return login(request, AuthClient.WEB);
    }

    @Transactional
    public AuthResult login(LoginRequest request, AuthClient client) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }
        UserEntity user = userMapper.findByUsername(request.username().strip().toLowerCase(Locale.ROOT));
        boolean matches = passwordEncoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches || !user.isEnabled()) {
            throw invalidCredentials();
        }
        return new AuthResult(UserProfile.from(user), sessionService.create(user.getId(), client));
    }

    public UserProfile profile(long uid) {
        UserEntity user = userMapper.findById(uid);
        if (user == null || !user.isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "账号不可用，请重新登录");
        }
        return UserProfile.from(user);
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码不正确");
    }

    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
