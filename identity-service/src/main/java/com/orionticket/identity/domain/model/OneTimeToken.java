package com.orionticket.identity.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Token de un solo uso para verificación de email o recuperación de
 * contraseña (Fase 3, C2 — patrón GoTrue).
 *
 * <p>El token opaco se genera con {@link java.security.SecureRandom} y se
 * persiste como SHA-256 ({@code tokenHash}); el valor en claro se entrega
 * al cliente una única vez y se envía por email vía evento RabbitMQ.</p>
 */
@Data
@Builder
public class OneTimeToken {

    public enum TokenType {
        EMAIL_VERIFICATION,
        PASSWORD_RECOVERY
    }

    private UUID tokenId;
    private UUID userId;
    /** SHA-256 del token opaco, en hexadecimal (64 chars). Nunca el token en claro. */
    private String tokenHash;
    private TokenType tokenType;
    private Instant createdAt;
    private Instant expiresAt;
    /** Instant en que se consumió; null si aún no se ha usado. */
    private Instant usedAt;

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    /** Un token es válido solo si no se ha usado ni ha expirado. */
    public boolean isValid() {
        return !isUsed() && !isExpired();
    }

    public void markUsed() {
        if (this.usedAt == null) {
            this.usedAt = Instant.now();
        }
    }
}
