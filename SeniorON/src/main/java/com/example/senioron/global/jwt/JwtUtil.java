package com.example.senioron.global.jwt;

import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String ADMIN_ACCESS_TOKEN_TYPE = "admin_access";
    private static final String ADMIN_REFRESH_TOKEN_TYPE = "admin_refresh";

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final SecretKey adminSecretKey;
    private final long adminAccessTokenExpiration;
    private final long adminRefreshTokenExpiration;

    @Autowired
    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
            @Value("${jwt.admin-secret}") String adminSecret,
            @Value("${jwt.admin-access-token-expiration}") long adminAccessTokenExpiration,
            @Value("${jwt.admin-refresh-token-expiration}") long adminRefreshTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.adminSecretKey = Keys.hmacShaKeyFor(adminSecret.getBytes(StandardCharsets.UTF_8));
        this.adminAccessTokenExpiration = adminAccessTokenExpiration;
        this.adminRefreshTokenExpiration = adminRefreshTokenExpiration;
    }

    public String createAccessToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(user.getUsersId()))
                .claim("loginId", user.getLoginId())
                .claim("name", user.getName())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getUsersId()))
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public String createAdminAccessToken(Admin admin) {
        return createAdminToken(admin, ADMIN_ACCESS_TOKEN_TYPE, adminAccessTokenExpiration, false);
    }

    public String createAdminRefreshToken(Admin admin) {
        return createAdminToken(admin, ADMIN_REFRESH_TOKEN_TYPE, adminRefreshTokenExpiration, true);
    }

    private String createAdminToken(Admin admin, String type, long expirationMillis, boolean includeId) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(String.valueOf(admin.getAdminId()))
                .claim(TOKEN_TYPE_CLAIM, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis));

        if (includeId) {
            builder.id(UUID.randomUUID().toString());
        }

        return builder.signWith(adminSecretKey).compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUsersId(String token) {
        return getUsersId(parseClaims(token));
    }

    public Long getUsersId(Claims claims) {
        String subject = claims.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject is missing");
        }

        return Long.valueOf(subject);
    }

    public boolean isAccessToken(String token) {
        return isAccessToken(parseClaims(token));
    }

    public boolean isAccessToken(Claims claims) {
        String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
        return tokenType == null || ACCESS_TOKEN_TYPE.equals(tokenType);
    }

    public boolean isRefreshToken(String token) {
        return isRefreshToken(parseClaims(token));
    }

    public boolean isRefreshToken(Claims claims) {
        return REFRESH_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public LocalDateTime getRefreshTokenExpiresAt() {
        return LocalDateTime.now().plus(Duration.ofMillis(refreshTokenExpiration));
    }

    public Claims parseAdminClaims(String token) {
        return Jwts.parser()
                .verifyWith(adminSecretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getAdminId(Claims claims) {
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject is missing");
        }
        return Long.valueOf(subject);
    }

    public boolean isAdminAccessToken(Claims claims) {
        return ADMIN_ACCESS_TOKEN_TYPE.equals(
                claims.get(TOKEN_TYPE_CLAIM, String.class)
        );
    }

    public boolean isAdminRefreshToken(Claims claims) {
        return ADMIN_REFRESH_TOKEN_TYPE.equals(
                claims.get(TOKEN_TYPE_CLAIM, String.class)
        );
    }

    public LocalDateTime getAdminRefreshTokenExpiresAt() {
        return LocalDateTime.now().plus(
                Duration.ofMillis(adminRefreshTokenExpiration)
        );
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
