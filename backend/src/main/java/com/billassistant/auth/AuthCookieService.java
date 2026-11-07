package com.billassistant.auth;

import com.billassistant.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieService {

    public static final String ACCESS_COOKIE = "BILL_ACCESS";
    public static final String REFRESH_COOKIE = "BILL_REFRESH";
    private final AuthProperties properties;

    public AuthCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    public String readAccess(HttpServletRequest request) {
        return read(request, ACCESS_COOKIE);
    }

    public String readRefresh(HttpServletRequest request) {
        return read(request, REFRESH_COOKIE);
    }

    private String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) return cookie.getValue();
            }
        }
        return null;
    }

    public void write(HttpServletResponse response, TokenPair pair) {
        setCookie(response, ACCESS_COOKIE, pair.accessToken(), "/api", pair.accessMaxAgeSeconds());
        setCookie(response, REFRESH_COOKIE, pair.refreshToken(), "/api/auth", pair.refreshMaxAgeSeconds());
        setCookie(response, "BILL_SESSION", "", "/api", 0);
    }

    public void clear(HttpServletResponse response) {
        setCookie(response, ACCESS_COOKIE, "", "/api", 0);
        setCookie(response, REFRESH_COOKIE, "", "/api/auth", 0);
        setCookie(response, "BILL_SESSION", "", "/api", 0);
    }

    private void setCookie(HttpServletResponse response, String name, String token, String path, long maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, token)
                .httpOnly(true).secure(properties.cookieSecure()).sameSite("Lax")
                .path(path).maxAge(maxAge).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
