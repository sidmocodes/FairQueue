package com.fairqueue.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class TokenService {
    
    private final SecretKey secretKey;
    private final long tokenValiditySeconds;
    
    public TokenService(
            @Value("${fairqueue.token.secret:defaultSecretKeyThatShouldBeChangedInProduction123456789}") String secret,
            @Value("${fairqueue.token.validity:3600}") long tokenValiditySeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.tokenValiditySeconds = tokenValiditySeconds;
    }
    
    public String generateQueueToken(String userId, String eventId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(tokenValiditySeconds);
        
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(userId)
                .claim("eventId", eventId)
                .claim("type", "queue")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }
    
    public Claims validateAndParse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    public String generateAdmissionPass(String userId, String eventId, long validitySeconds) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(validitySeconds);
        
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(userId)
                .claim("eventId", eventId)
                .claim("type", "admission")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }
    
    public boolean isTokenValid(String token) {
        try {
            validateAndParse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
