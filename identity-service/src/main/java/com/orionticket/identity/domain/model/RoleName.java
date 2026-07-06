package com.orionticket.identity.domain.model;

/**
 * Fuente única de verdad para los nombres de roles del sistema.
 *
 * <p>Cualquier referencia a un rol por nombre debe usar este enum en lugar
 * de strings sueltos o UUIDs mágicos. Esto permite refactor seguros y
 * búsqueda estática de todos los usos de un rol.</p>
 */
public enum RoleName {
    SUPER_ADMIN,
    ORGANIZER,
    BUYER,
    VENUE_STAFF,
    DOOR_VALIDATOR;

    /**
     * Devuelve el nombre canónico usado en la BD y en los claims del JWT.
     */
    public String value() {
        return name();
    }
}
