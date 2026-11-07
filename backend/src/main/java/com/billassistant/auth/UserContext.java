package com.billassistant.auth;

import com.billassistant.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Request-scoped identity; no ThreadLocal value can leak into a reused request thread. */
public final class UserContext {

    static final String UID_ATTRIBUTE = UserContext.class.getName() + ".uid";

    private UserContext() {
    }

    public static long requireUid() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute(UID_ATTRIBUTE) instanceof Long uid) {
            return uid;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
    }
}
