package com.issuemoa.users.infrastructure.common;

import io.jsonwebtoken.Claims;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public class RequestUtil {

    public static Claims getClaims() {
        Claims claims = (Claims) RequestContextHolder.getRequestAttributes().getAttribute("claims", RequestAttributes.SCOPE_REQUEST);
        if (claims == null) {
            throw new NullPointerException("==> RequestUtil getClaims");
        }
        return claims;
    }

    public static long getUserId() {
        return (long) ((int) getClaims().get("id"));
    }

    public static String getUserEmail() {
        return getClaims().getSubject();
    }
}
