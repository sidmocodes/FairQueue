package com.fairqueue.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    private TokenService tokenService;
    private static final String TEST_SECRET = "testSecretKeyThatIsLongEnoughForHS256Algorithm12345";
    private static final long TOKEN_VALIDITY = 3600;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(TEST_SECRET, TOKEN_VALIDITY);
    }

    @Test
    void generateQueueToken_shouldCreateValidToken() {
        // Given
        String userId = "user123";
        String eventId = "event456";

        // When
        String token = tokenService.generateQueueToken(userId, eventId);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        
        Claims claims = tokenService.validateAndParse(token);
        assertEquals(userId, claims.getSubject());
        assertEquals(eventId, claims.get("eventId"));
        assertEquals("queue", claims.get("type"));
        assertNotNull(claims.getId());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void generateAdmissionPass_shouldCreateValidToken() {
        // Given
        String userId = "user789";
        String eventId = "event123";
        long validitySeconds = 300;

        // When
        String token = tokenService.generateAdmissionPass(userId, eventId, validitySeconds);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        
        Claims claims = tokenService.validateAndParse(token);
        assertEquals(userId, claims.getSubject());
        assertEquals(eventId, claims.get("eventId"));
        assertEquals("admission", claims.get("type"));
    }

    @Test
    void validateAndParse_shouldParseValidToken() {
        // Given
        String userId = "user123";
        String eventId = "event456";
        String token = tokenService.generateQueueToken(userId, eventId);

        // When
        Claims claims = tokenService.validateAndParse(token);

        // Then
        assertNotNull(claims);
        assertEquals(userId, claims.getSubject());
        assertEquals(eventId, claims.get("eventId"));
    }

    @Test
    void validateAndParse_shouldRejectMalformedToken() {
        // Given
        String malformedToken = "not.a.valid.token";

        // When/Then
        assertThrows(MalformedJwtException.class, () -> {
            tokenService.validateAndParse(malformedToken);
        });
    }

    @Test
    void validateAndParse_shouldRejectTokenWithWrongSignature() {
        // Given
        TokenService otherService = new TokenService("differentSecret1234567890123456789012345678", TOKEN_VALIDITY);
        String token = otherService.generateQueueToken("user123", "event456");

        // When/Then
        assertThrows(SignatureException.class, () -> {
            tokenService.validateAndParse(token);
        });
    }

    @Test
    void validateAndParse_shouldRejectExpiredToken() {
        // Given
        TokenService shortLivedService = new TokenService(TEST_SECRET, -1); // Expired immediately
        String token = shortLivedService.generateQueueToken("user123", "event456");

        // When/Then
        assertThrows(ExpiredJwtException.class, () -> {
            tokenService.validateAndParse(token);
        });
    }

    @Test
    void isTokenValid_shouldReturnTrueForValidToken() {
        // Given
        String token = tokenService.generateQueueToken("user123", "event456");

        // When
        boolean isValid = tokenService.isTokenValid(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    void isTokenValid_shouldReturnFalseForInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        boolean isValid = tokenService.isTokenValid(invalidToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void isTokenValid_shouldReturnFalseForExpiredToken() {
        // Given
        TokenService shortLivedService = new TokenService(TEST_SECRET, -1);
        String token = shortLivedService.generateQueueToken("user123", "event456");

        // When
        boolean isValid = tokenService.isTokenValid(token);

        // Then
        assertFalse(isValid);
    }

    @Test
    void generateQueueToken_shouldCreateUniqueTokens() {
        // Given
        String userId = "user123";
        String eventId = "event456";

        // When
        String token1 = tokenService.generateQueueToken(userId, eventId);
        String token2 = tokenService.generateQueueToken(userId, eventId);

        // Then
        assertNotEquals(token1, token2, "Tokens should be unique even for same user/event");
        
        Claims claims1 = tokenService.validateAndParse(token1);
        Claims claims2 = tokenService.validateAndParse(token2);
        assertNotEquals(claims1.getId(), claims2.getId());
    }

    @Test
    void generateAdmissionPass_shouldRespectCustomValidity() {
        // Given
        String userId = "user123";
        String eventId = "event456";
        long customValidity = 600;

        // When
        String token = tokenService.generateAdmissionPass(userId, eventId, customValidity);
        Claims claims = tokenService.validateAndParse(token);

        // Then
        long actualValidity = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertTrue(Math.abs(actualValidity - customValidity) <= 1, "Validity should match custom value");
    }
}
