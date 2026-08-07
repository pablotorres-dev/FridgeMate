package org.example.fridgecalories.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** Issues and verifies the signed tokens that keep a user logged in. */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration lifetime;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-days}") long expirationDays) {
        this.key = deriveKey(secret);
        this.lifetime = Duration.ofDays(expirationDays);
    }

    /**
     * HMAC-SHA256 needs a 256-bit key, but a configured secret is just whatever
     * string the deployment happened to set. Hashing it always yields exactly
     * 256 bits, so any secret works and the app can't fail to start over the
     * length of an environment variable. The same input always derives the same
     * key, so existing sessions stay valid.
     */
    private static SecretKey deriveKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required to sign auth tokens", e);
        }
    }

    public Duration getLifetime() {
        return lifetime;
    }

    public String generateToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    /**
     * @return the username the token was issued for, or {@code null} if the token
     *         is expired, tampered with, or otherwise unusable.
     */
    public String extractUsername(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
