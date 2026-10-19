package com.example.hr_management_backend.core.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private int jwtExpirationMs;

    private final java.util.concurrent.ConcurrentHashMap<String, Long> userRevocationTimeMap =
            new java.util.concurrent.ConcurrentHashMap<>();

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public void revokeUserTokens(String email) {
        if (email != null && !email.isBlank()) {
            userRevocationTimeMap.put(email.trim().toLowerCase(), System.currentTimeMillis());
        }
    }

    public boolean isTokenRevoked(String email, Date issuedAt) {
        if (email == null || issuedAt == null) return false;
        Long revokedAt = userRevocationTimeMap.get(email.trim().toLowerCase());
        return revokedAt != null && issuedAt.getTime() < revokedAt;
    }

    public String generateJwtToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        java.util.List<String> authorities = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .toList();

        var builder = Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim("authorities", authorities)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs));

        if (userPrincipal instanceof UserPrincipal up) {
            if (up.getId() != null) builder.claim("userId", up.getId());
            if (up.getEmployeeId() != null) builder.claim("employeeId", up.getEmployeeId());
            if (up.getRole() != null) builder.claim("role", up.getRole());
            if (up.getSystemRole() != null) builder.claim("systemRole", up.getSystemRole());
        }

        return builder.signWith(key(), Jwts.SIG.HS256).compact();
    }

    public String generateTokenFromEmail(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public Date getIssuedAtFromJwtToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getIssuedAt();
        } catch (Exception ignored) {
            return null;
        }
    }

    public Long getClaimLong(String token, String claimName) {
        try {
            Claims claims = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
            Object val = claims.get(claimName);
            if (val instanceof Number n) {
                return n.longValue();
            } else if (val != null) {
                return Long.parseLong(val.toString());
            }
        } catch (Exception ignored) {}
        return null;
    }

    public String getClaimString(String token, String claimName) {
        try {
            Claims claims = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
            Object val = claims.get(claimName);
            return val != null ? val.toString() : null;
        } catch (Exception ignored) {}
        return null;
    }

    @SuppressWarnings("unchecked")
    public java.util.List<String> getAuthoritiesFromJwtToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Object authObj = claims.get("authorities");
            if (authObj instanceof java.util.List<?> list) {
                return list.stream().map(Object::toString).toList();
            }
        } catch (Exception ignored) {}
        return java.util.List.of();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Claims claims = Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken).getPayload();
            String username = claims.getSubject();
            Date iat = claims.getIssuedAt();
            if (isTokenRevoked(username, iat)) {
                return false;
            }
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Invalid JWT signature, expired, or malformed
        }
        return false;
    }
}
