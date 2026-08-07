package org.example.fridgecalories.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Arrays;

/**
 * The auth token travels in a cookie rather than a response body so the browser
 * sends it automatically and JavaScript can never read it — an XSS bug then
 * can't leak the token, unlike the common localStorage approach.
 */
public final class AuthCookies {

    public static final String TOKEN_COOKIE = "fridgemate_token";

    private AuthCookies() {
    }

    public static ResponseCookie issue(String token, Duration lifetime) {
        return baseCookie(token).maxAge(lifetime).build();
    }

    public static ResponseCookie clear() {
        return baseCookie("").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(TOKEN_COOKIE, value)
                .httpOnly(true)
                // Sent over HTTPS only in production; browsers make an exception
                // for localhost, so local development still works.
                .secure(true)
                .path("/")
                // Blocks the cookie on cross-site POSTs, which is the CSRF vector
                // that matters here, while normal navigation keeps working.
                .sameSite("Lax");
    }

    public static String readToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> TOKEN_COOKIE.equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
