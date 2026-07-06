package com.orionticket.identity.application.port.out;

import java.util.UUID;

/**
 * Puerto de auditoría para eventos de autenticación y administración.
 *
 * <p>Los eventos de autenticación (login, refresh, logout, registro)
 * deben incluir IP y user-agent para investigación de incidentes.
 * Nunca se loguea la contraseña ni el token en claro.</p>
 */
public interface AuditLogPort {

    /**
     * Registra un evento de auditoría con contexto de red (IP + user-agent).
     *
     * @param actorId   el ID del usuario que realiza la acción (puede ser null para eventos anónimos).
     * @param action    el tipo de evento (p. ej. {@code LOGIN_SUCCESS}, {@code LOGIN_FAILED}).
     * @param details   detalles legibles del evento (sin datos sensibles).
     * @param ipAddress la IP del cliente (puede ser null).
     * @param userAgent el user-agent del cliente (puede ser null).
     */
    void logAction(UUID actorId, String action, String details, String ipAddress, String userAgent);

    /**
     * Registra un evento de auditoría sin contexto de red.
     * Se usa para acciones administrativas donde la IP/user-agent no están
     * disponibles o no son relevantes.
     */
    default void logAction(UUID adminId, String action, String details) {
        logAction(adminId, action, details, null, null);
    }
}
