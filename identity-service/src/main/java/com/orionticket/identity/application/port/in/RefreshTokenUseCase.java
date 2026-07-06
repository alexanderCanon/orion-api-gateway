package com.orionticket.identity.application.port.in;

/**
 * Caso de uso de refresco de token.
 *
 * <p>Recibe un refresh token opaco, valida que sea activo, verifica que el
 * usuario siga habilitado, y rota: revoca el token presentado y emite un
 * nuevo par access + refresh encadenado al anterior vía {@code parentId}.</p>
 *
 * <p><b>Detección de reuso:</b> si el token presentado ya está revocado
 * (porque ya se rotó), se asume compromiso del token y se revoca toda la
 * cadena del usuario.</p>
 */
public interface RefreshTokenUseCase {
    AuthResult refresh(String rawRefreshToken, String userAgent, String ipAddress);
}
