package com.orionticket.identity.infrastructure.adapters.out.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecureRandomRefreshTokenGeneratorTest {

    private final SecureRandomRefreshTokenGenerator generator = new SecureRandomRefreshTokenGenerator();

    @Test
    void generateProducesUrlSafeBase64WithoutPadding() {
        String token = generator.generate();
        assertNotNull(token);
        assertFalse(token.isEmpty());
        // 32 bytes → Base64 URL-safe sin padding = 43 chars
        assertEquals(43, token.length());
        // No debe contener padding
        assertFalse(token.contains("="));
        // Debe ser URL-safe (solo A-Z, a-z, 0-9, -, _)
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void generateProducesUniqueTokens() {
        String t1 = generator.generate();
        String t2 = generator.generate();
        assertNotEquals(t1, t2);
    }

    @Test
    void hashProduces64CharHexString() {
        String hash = generator.hash("some-token-value");
        assertNotNull(hash);
        // SHA-256 → 32 bytes → 64 hex chars
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void hashIsDeterministic() {
        String token = "deterministic-test";
        String h1 = generator.hash(token);
        String h2 = generator.hash(token);
        assertEquals(h1, h2);
    }

    @Test
    void hashOfDifferentTokensDiffers() {
        String h1 = generator.hash("token-a");
        String h2 = generator.hash("token-b");
        assertNotEquals(h1, h2);
    }

    @Test
    void hashOfEmptyStringIsKnownConstant() {
        // SHA-256 of empty string = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        String hash = generator.hash("");
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }
}
