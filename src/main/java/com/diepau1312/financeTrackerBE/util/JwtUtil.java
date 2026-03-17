package com.diepau1312.financeTrackerBE.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    // ─── Tạo SecretKey từ string trong config ─────────────────────────────────
    // Gọi mỗi lần thay vì lưu field — tránh vấn đề khi secret chưa được inject
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ─── Tạo access token — nhúng email và planId vào claims ──────────────────
    public String generateToken(String email, String planId) {
        return Jwts.builder()
                .subject(email)
                .claim("planId", planId)       // planId dùng cho @RequiresPlan
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // ─── Đọc toàn bộ claims từ token ──────────────────────────────────────────
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ─── Các helper methods đọc từng field ────────────────────────────────────
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractPlanId(String token) {
        return extractClaims(token).get("planId", String.class);
    }

    public boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    public boolean isTokenValid(String token, String email) {
        return extractEmail(token).equals(email) && !isTokenExpired(token);
    }
}