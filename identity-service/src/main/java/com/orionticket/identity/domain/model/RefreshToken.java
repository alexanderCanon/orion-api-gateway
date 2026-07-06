package com.orionticket.identity.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Token de refresco opaco y rotativo.
 *
 * <p>Diseño tipo GoTrue: el token opaco se guarda como SHA-256 (nunca en
 * claro en BD), se rota en cada uso y se encadena vía {@code parentId} para
 * detectar reuso. Si un token ya rotado se presenta de nuevo, se revoca
 * toda la cadena del usuario.</p>
 *
 * <p>El token en claro solo existe en memoria durante la generación y se
 * entrega al cliente una única vez; la BD solo persiste {@code tokenHash}.</p>
 */
@Data
@Builder
public class RefreshToken {

    private UUID tokenId;
    private UUID userId;
    /** SHA-256 del token opaco, en hexadecimal (64 chars). Nunca el token en claro. */
    private String tokenHash;
    /** Token padre del que se rotó; null para el primer token de una cadena. */
    private UUID parentId;
    private Instant issuedAt;
    private Instant expiresAt;
    /** Instant en que se revocó (por logout, rotación, suspensión, etc.); null si activo. */
    private Instant revokedAt;
    private String userAgent;
    private String ipAddress;

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Un token es válido solo si no está revocado ni expirado. */
    public boolean isValid() {
        return !isRevoked() && !isExpired();
    }

    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }
}
