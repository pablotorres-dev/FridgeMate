package org.example.fridgecalories.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
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
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.lifetime = Duration.ofDays(expirationDays);
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
