package ar.com.viajar.security;

import ar.com.viajar.exception.AppException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.refresh-secret}") String refreshSecret,
            @Value("${jwt.access-expiration-ms}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs
    ) {
        this.accessKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(UUID userId) {
        return buildToken(userId.toString(), accessKey, accessExpirationMs);
    }

    public String generateRefreshToken(UUID userId) {
        return buildToken(userId.toString(), refreshKey, refreshExpirationMs);
    }

    public UUID extractUserIdFromAccess(String token) {
        return extractUserId(token, accessKey);
    }

    public UUID extractUserIdFromRefresh(String token) {
        return extractUserId(token, refreshKey);
    }

    private String buildToken(String userId, SecretKey key, long expirationMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claim("userId", userId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key)
                .compact();
    }

    private UUID extractUserId(String token, SecretKey key) {
        try {
            String userId = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get("userId", String.class);
            return UUID.fromString(userId);
        } catch (JwtException | IllegalArgumentException e) {
            throw AppException.unauthorized("Token inválido o expirado");
        }
    }
}
