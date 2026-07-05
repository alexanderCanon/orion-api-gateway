package com.orionticket.identity.application.port.in;

/**
 * Caso de uso de confirmación de recuperación de contraseña (reset password).
 *
 * <p>Valida el token de un solo uso, actualiza la contraseña, revoca todas
 * las sesiones activas del usuario e invalida otros tokens de recovery
 * pendientes.</p>
 */
public interface ResetPasswordUseCase {
    /**
     * Restablece la contraseña usando un token de recuperación válido.
     *
     * @param rawToken     el token opaco recibido por email.
     * @param newPassword  la nueva contraseña (ya validada por el controller).
     */
    void resetPassword(String rawToken, String newPassword);
}
