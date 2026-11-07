package com.billassistant.auth;

import com.billassistant.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/health", "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
            "/api/app/auth/register", "/api/app/auth/login", "/api/app/auth/refresh", "/api/app/auth/logout");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final AuthCookieService cookieService;
    private final AccessTokenService accessTokens;

    public AuthInterceptor(AuthCookieService cookieService, AccessTokenService accessTokens) {
        this.cookieService = cookieService;
        this.accessTokens = accessTokens;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.removeAttribute(UserContext.UID_ATTRIBUTE);
        response.setHeader("Cache-Control", "no-store");
        // Browsers cannot add this header to cross-origin requests without an accepted CORS preflight.
        if (!SAFE_METHODS.contains(request.getMethod())
                && !"BillAssistant".equals(request.getHeader("X-Requested-With"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "请求缺少有效的来源标识");
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean bearer = authorization != null;
        String token = bearer ? readBearer(authorization) : cookieService.readAccess(request);
        var identity = accessTokens.verify(token);
        if (identity == null || identity.client() != (bearer ? AuthClient.APP : AuthClient.WEB)
                || (path.startsWith("/api/app/") && !bearer)) {
            // Do not clear cookies: a delayed 401 must not erase a newly refreshed token pair.
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCESS_TOKEN_INVALID", "访问凭证已失效，请刷新登录态");
        }
        request.setAttribute(UserContext.UID_ATTRIBUTE, identity.uid());
        return true;
    }

    private String readBearer(String authorization) {
        // An explicitly supplied invalid header must never fall back to a browser cookie.
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = authorization.substring(7);
        return token.isBlank() || token.contains(" ") ? null : token;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        request.removeAttribute(UserContext.UID_ATTRIBUTE);
    }
}
