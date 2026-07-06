package com.orionticket.identity.domain.port.out;

import com.orionticket.identity.domain.model.OneTimeToken;
import com.orionticket.identity.domain.model.OneTimeToken.TokenType;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia para tokens de un solo uso.
 */
public interface OneTimeTokenRepositoryPort {

    OneTimeToken save(OneTimeToken token);

    Optional<OneTimeToken> findByTokenHashAndType(String tokenHash, TokenType tokenType);

    /**
     * Marca como usados todos los tokens pendientes del usuario para el tipo
     * dado. Se usa tras un cambio de contraseña o verificación exitosa para
     * invalidar tokens anteriores del mismo tipo.
     */
    void markAllUnusedForUserAndType(UUID userId, TokenType tokenType);

    /**
     * Cuenta los tokens activos (no usados, no expirados) del usuario para el
     * tipo dado. Se usa para rate limiting de reenvío (1 token cada 60s).
     */
    long countActiveForUserAndType(UUID userId, TokenType tokenType);
}
