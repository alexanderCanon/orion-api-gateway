package com.orionticket.identity.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void passwordEncoderProducesBcryptPrefixedHashForNewPasswords() {
        // Fase 5.5: el DelegatingPasswordEncoder debe generar hashes con
        // prefijo {bcrypt} para nuevos passwords.
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String hash = encoder.encode("mySecret123");

        assertTrue(hash.startsWith("{bcrypt}"),
                "New hashes must be prefixed with {bcrypt} for delegation");
    }

    @Test
    void passwordEncoderValidatesNewBcryptPrefixedHash() {
        // Fase 5.5: un hash generado por el encoder debe validarse correctamente.
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String hash = encoder.encode("mySecret123");

        assertTrue(encoder.matches("mySecret123", hash));
        assertFalse(encoder.matches("wrongPassword", hash));
    }

    @Test
    void passwordEncoderValidatesLegacyHashWithoutPrefix() {
        // Fase 5.5: los hashes existentes sin prefijo (generados antes de
        // esta fase con BCryptPasswordEncoder plano) deben seguir validando
        // gracias a setDefaultPasswordEncoderForMatches.
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        // Generar un hash legacy sin prefijo usando BCryptPasswordEncoder strength 10.
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder legacyEncoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10);
        String legacyHash = legacyEncoder.encode("myLegacyPassword");

        assertTrue(encoder.matches("myLegacyPassword", legacyHash),
                "Legacy hashes without {bcrypt} prefix must still validate");
        assertFalse(encoder.matches("wrongPassword", legacyHash));
    }

    @Test
    void passwordEncoderUsesBcryptStrength12() {
        // Fase 5.5: el strength de bcrypt para nuevos hashes debe ser 12.
        // Verificamos que el costo de un hash nuevo sea 12 (el prefijo $2a$12$).
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String hash = encoder.encode("test123");

        // El hash tiene formato {bcrypt}$2a$12$...
        String bcryptPart = hash.substring("{bcrypt}".length());
        assertTrue(bcryptPart.startsWith("$2a$12$"),
                "New bcrypt hashes must use strength 12, got: " + bcryptPart.substring(0, 8));
    }
}
