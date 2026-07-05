package com.orionticket.identity.application.port.in;

import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.model.User;
import lombok.Builder;

/**
 * Resultado de una autenticación exitosa (login o refresh).
 *
 * <p>Contiene el access token JWT de corta vida y el refresh token opaco
 * rotativo, junto con los metadatos que el cliente necesita, incluyendo
 * el rol resuelto para evitar consultas duplicadas en el controller.</p>
 */
@Builder
public record AuthResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        User user,
        Role role
) {
    public static final String TOKEN_TYPE = "Bearer";
}
