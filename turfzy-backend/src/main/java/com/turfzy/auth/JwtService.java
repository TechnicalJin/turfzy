package com.turfzy.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * Handles all JWT operations: generate, validate, extract claims.
 *
 * Uses jjwt 0.12.x fluent API.
 * Algorithm: HS256 (HMAC-SHA256) — symmetric, sufficient for monolith.
 * For microservices, RS256 (asymmetric) would be preferred so each service
 * can verify without sharing the secret — a common interview follow-up.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    // ----- Token Generation -----

    /** Generate access token with user email as subject + extra claims */
    public String generateAccessToken(String email, Map<String, Object> extraClaims) {
        return buildToken(email, extraClaims, jwtExpirationMs);
    }

    /** Generate refresh token — minimal claims, longer expiry */
    public String generateRefreshToken(String email) {
        return buildToken(email, Map.of("type", "refresh"), refreshExpirationMs);
    }

    private String buildToken(String subject, Map<String, Object> claims, long expiryMs) {
        return Jwts.builder()
            .claims(claims)
            .subject(subject)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiryMs))
            .signWith(getSigningKey())
            .compact();
    }

    // ----- Token Validation -----

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ----- Claims Extraction -----

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Safe parse — returns null instead of throwing on invalid tokens */
    public String safeExtractEmail(String token) {
        try {
            return extractEmail(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return null;
        }
    }
}