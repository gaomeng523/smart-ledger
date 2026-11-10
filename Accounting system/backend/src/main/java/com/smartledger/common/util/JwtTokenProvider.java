package com.smartledger.common.util;

import com.smartledger.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析。
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey secretKey;

    @PostConstruct
    void init() {
        byte[] bytes = jwtProperties.getSecret() == null
                ? new byte[0]
                : jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("smartledger.jwt.secret 长度不足 32 字节，HS256 无法使用");
        }
        this.secretKey = Keys.hmacShaKeyFor(bytes);
    }

    /**
     * 签发 token，subject 为 openid。
     */
    public String createToken(String openid) {
        long expireMillis = jwtProperties.getExpireDays() * 24L * 60L * 60L * 1000L;
        Date now = new Date();
        return Jwts.builder()
                .subject(openid)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验 token，返回 openid；非法或过期时抛出 JwtException。
     */
    public String parseOpenid(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload().getSubject();
    }

    /**
     * token 有效期（秒），供登录接口返回。
     */
    public long getExpireSeconds() {
        return jwtProperties.getExpireDays() * 24L * 60L * 60L;
    }
}