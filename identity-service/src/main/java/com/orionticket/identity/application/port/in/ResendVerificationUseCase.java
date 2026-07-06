package com.orionticket.identity.application.port.in;

/**
 * Caso de uso de reenvío del email de verificación.
 *
 * <p>Responde siempre 200 OK exista o no el email (anti-enumeración).
 * Rate limited: 1 reenvío cada 60s por usuario.</p>
 */
public interface ResendVerificationUseCase {
    /**
     * Reenvía el email de verificación si el usuario existe y aún no ha
     * verificado su email.
     *
     * @param email el email de la cuenta.
     */
    void resendVerification(String email);
}
