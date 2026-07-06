package com.orionticket.identity.application.port.in;

import java.util.UUID;

/**
 * Caso de uso de cambio de contraseña autenticado.
 *
 * <p>Requiere JWT: verifica la contraseña actual, actualiza a la nueva,
 * revoca todos los refresh tokens del usuario (excepto la sesión actual
 * identificada por su refresh token) y audita el cambio.</p>
 */
public interface ChangePasswordUseCase {
    /**
     * Cambia la contraseña del usuario autenticado.
     *
     * @param userId           el ID del usuario (extraído del JWT).
     * @param currentPassword  la contraseña actual en claro.
     * @param newPassword      la nueva contraseña (ya validada por el controller).
     * @param currentRefreshToken el refresh token de la sesión actual (no se revoca).
     */
    void changePassword(UUID userId, String currentPassword, String newPassword, String currentRefreshToken);
}
