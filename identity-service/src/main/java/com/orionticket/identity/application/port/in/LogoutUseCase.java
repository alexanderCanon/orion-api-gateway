package com.orionticket.identity.application.port.in;

/**
 * Caso de uso de logout.
 *
 * <p>Revoca el refresh token presentado. Opcionalmente, con {@code all=true},
 * revoca todos los refresh tokens del usuario al que pertenece el token
 * (logout de todas las sesiones).</p>
 */
public interface LogoutUseCase {

    /** Revoca solo el token presentado. */
    void logout(String rawRefreshToken);

    /** Revoca el token presentado y, si {@code all=true}, todos los del usuario. */
    void logout(String rawRefreshToken, boolean all);
}
