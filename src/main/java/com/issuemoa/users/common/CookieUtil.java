package com.issuemoa.users.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

@Slf4j
public class CookieUtil {

    public static Cookie setCookie(String name, String value, long expires, boolean httpOnly) {
        if (!StringUtils.hasText(value))
            throw new NullPointerException("==> RefreshToken.");
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge((int) expires);
        cookie.setHttpOnly(httpOnly);
        return cookie;
    }

    public static String getRefreshTokenCookie(HttpServletRequest request) {
        String refreshToken = "";

        for (Cookie cookie:request.getCookies())
            if (cookie.getName().equals("refreshToken"))
                refreshToken = cookie.getValue();

        return refreshToken;
    }
}