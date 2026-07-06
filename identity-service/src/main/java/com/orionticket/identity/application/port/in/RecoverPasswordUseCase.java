package com.orionticket.identity.application.port.in;

/**
 * Caso de uso de recuperación de contraseña (forgot password).
 *
 * <p>Responde siempre 200 OK exista o no el email (anti-enumeración).
 * Si el email existe, genera un token de un solo uso y publica un evento
 * para el envío del email de recuperación.</p>
 */
public interface RecoverPasswordUseCase {
    /**
     * Inicia el flujo de recuperación de contraseña.
     *
     * @param email el email de la cuenta a recuperar.
     */
    void requestPasswordRecovery(String email);
}
