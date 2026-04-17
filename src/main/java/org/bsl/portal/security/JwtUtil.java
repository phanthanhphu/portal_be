package org.bsl.portal.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.bsl.portal.controller.UserController;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private final String jwtSecret = "VerySecretKeyThatIsAtLeast256bitsLongForHS256Algorithm!!!"; // Phải dài đủ
    private final int jwtExpirationMs = 24 * 60 * 60 * 1000; // 1 ngày

    private Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());

    // ✅ THÊM: Reference đến UserController để check blacklist
    @Autowired
    private UserController userController;

    // ==================== GENERATE TOKEN (GIỮ NGUYÊN) ====================
    public String generateToken(String email, String role, long tokenVersion) {
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .claim("tokenVersion", tokenVersion) // Thêm tokenVersion vào payload
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(String email, String role) {
        return generateToken(email, role, 1L); // Default version = 1
    }

    // ==================== ✅ FIX: THÊM validateToken(String) ====================
    public boolean validateToken(String token) {
        try {
            // 1. CHECK BLACKLIST TRƯỚC
            if (userController != null && userController.getBlacklistedTokens().contains(token)) {
                return false;
            }

            // 2. Parse token để check basic validity
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ==================== GIỮ NGUYÊN: validateToken 3 params ====================
    public boolean validateToken(String token, String email, long tokenVersion) {
        try {
            // 1. CHECK BLACKLIST TRƯỚC
            if (userController != null && userController.getBlacklistedTokens().contains(token)) {
                return false;
            }

            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            return claims.getSubject().equals(email) &&
                    claims.get("tokenVersion", Long.class) == tokenVersion &&
                    !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ==================== GET METHODS (CẬP NHẬT CHECK BLACKLIST) ====================
    public String getEmailFromToken(String token) {
        try {
            // CHECK BLACKLIST TRƯỚC
            if (userController != null && userController.getBlacklistedTokens().contains(token)) {
                return null;
            }

            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public String getRoleFromToken(String token) {
        try {
            // CHECK BLACKLIST TRƯỚC
            if (userController != null && userController.getBlacklistedTokens().contains(token)) {
                return null;
            }

            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            return claims.get("role", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long getTokenVersionFromToken(String token) {
        try {
            // CHECK BLACKLIST TRƯỚC
            if (userController != null && userController.getBlacklistedTokens().contains(token)) {
                return -1;
            }

            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            return claims.get("tokenVersion", Long.class);
        } catch (JwtException | IllegalArgumentException e) {
            return -1;
        }
    }

    // ==================== THÊM: Helper methods ====================
    /**
     * Check token có bị blacklist không
     */
    public boolean isTokenBlacklisted(String token) {
        return userController != null && userController.getBlacklistedTokens().contains(token);
    }

    /**
     * Check token expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}