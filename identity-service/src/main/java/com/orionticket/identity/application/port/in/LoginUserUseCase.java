package com.orionticket.identity.application.port.in;

/**
 * Caso de uso de login.
 *
 * <p>Devuelve un {@link AuthResult} con el access token JWT de corta vida
 * y el refresh token opaco rotativo. El refresh token se persiste hasheado
 * (SHA-256) para permitir la rotación y revocación.</p>
 */
public interface LoginUserUseCase {
    AuthResult login(String email, String rawPassword, String userAgent, String ipAddress);
}
