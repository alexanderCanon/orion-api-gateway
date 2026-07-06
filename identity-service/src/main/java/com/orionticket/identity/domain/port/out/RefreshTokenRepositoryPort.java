package com.orionticket.identity.domain.port.out;

import com.orionticket.identity.domain.model.RefreshToken;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia para refresh tokens.
 *
 * <p>El token opaco se persiste solo como hash (SHA-256); el token en claro
 * nunca se almacena. La búsqueda se hace por hash para validar el refresh
 * presentado por el cliente.</p>
 */
public interface RefreshTokenRepositoryPort {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoca (marca {@code revokedAt}) todos los tokens activos de un usuario. */
    int revokeAllForUser(UUID userId);

    /** Revoca toda la cadena de rotación a partir de un token (incluido él mismo). */
    int revokeChain(UUID tokenId);

    /** Cuenta los tokens activos (no revocados, no expirados) de un usuario. */
    long countActiveForUser(UUID userId);
}
