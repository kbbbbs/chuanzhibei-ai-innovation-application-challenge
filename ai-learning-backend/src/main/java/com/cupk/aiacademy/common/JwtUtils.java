package com.cupk.aiacademy.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    public String createToken(Integer userId, String username, String roleCode) {
        Date now = new Date();
        Date expireTime = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roleCode", roleCode)
                .issuedAt(now)
                .expiration(expireTime)
                .signWith(getSecretKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSecretKey() {
        String key = secret == null ? "" : secret.trim();
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
