package com.mickey.onlineordering.onlineorderingserver.util;

import com.mickey.onlineordering.onlineorderingserver.common.Constants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 * 用于生成和解析JWT Token
 */
@Slf4j
@Component
public class JwtUtil {
    
    /**
     * JWT密钥
     */
    private static final String SECRET_KEY = "OnlineOrderingSystemSecretKeyForJWT2025MickeyDevelopment";
    
    /**
     * Token有效期（毫秒）
     */
    private static final Long EXPIRATION_TIME = Constants.TOKEN_EXPIRE_TIME * 1000;
    
    /**
     * 获取密钥
     */
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * 生成Token
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param role 角色
     * @return Token字符串
     */
    public String generateToken(Long userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_TIME);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(getSecretKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * 解析Token
     *
     * @param token Token字符串
     * @return Claims对象
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSecretKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.error("解析Token失败：{}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从Token中获取用户ID
     *
     * @param token Token字符串
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            return claims.get("userId", Long.class);
        }
        return null;
    }
    
    /**
     * 从Token中获取用户名
     *
     * @param token Token字符串
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            return claims.getSubject();
        }
        return null;
    }
    
    /**
     * 从Token中获取角色
     *
     * @param token Token字符串
     * @return 角色
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            return claims.get("role", String.class);
        }
        return null;
    }
    
    /**
     * 验证Token是否有效
     *
     * @param token Token字符串
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            if (claims == null) {
                return false;
            }
            Date expiration = claims.getExpiration();
            return expiration.after(new Date());
        } catch (Exception e) {
            log.error("验证Token失败：{}", e.getMessage());
            return false;
        }
    }
}











