package com.orionticket.identity.infrastructure.adapters.out.security;

import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generador de refresh tokens opacos basado en {@link SecureRandom} (256 bits)
 * con hash SHA-256 para persistencia.
 */
@Component
public class SecureRandomRefreshTokenGenerator implements RefreshTokenGeneratorPort {

    private static final int TOKEN_BYTES = 32; // 256 bits de entropía

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
