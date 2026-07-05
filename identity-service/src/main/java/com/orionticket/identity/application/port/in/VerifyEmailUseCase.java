package com.orionticket.identity.application.port.in;

/**
 * Caso de uso de verificación de email.
 *
 * <p>Valida el token de verificación y transiciona la cuenta de
 * {@code UNVERIFIED} a {@code ACTIVE}.</p>
 */
public interface VerifyEmailUseCase {
    /**
     * Verifica el email usando un token de verificación válido.
     *
     * @param rawToken el token opaco recibido por email.
     */
    void verifyEmail(String rawToken);
}
