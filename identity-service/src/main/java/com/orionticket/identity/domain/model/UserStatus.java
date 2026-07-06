package com.orionticket.identity.domain.model;

/**
 * Estados posibles de una cuenta de usuario.
 *
 * <p>Se mantiene como enum tipado en el dominio aunque la persistencia
 * guarde el valor como String (ver {@code UserJpaEntity}). Los valores
 * deben coincidir exactamente con los usados en las migraciones Flyway
 * y en el código existente.</p>
 */
public enum UserStatus {

    /** Recién registrado, email aún no verificado. */
    UNVERIFIED,

    /** Cuenta activa y verificada. */
    ACTIVE,

    /** Cuenta suspendida administrativamente; no puede autenticarse. */
    SUSPENDED;

    /**
     * Indica si un usuario en este estado puede autenticarse.
     *
     * <p>Política actual (alineada con el plan de seguridad Fase 0):
     * {@code ACTIVE} y {@code UNVERIFIED} pueden hacer login (este último
     * hasta que se implemente la verificación obligatoria de email en
     * Fase 3). {@code SUSPENDED} nunca puede autenticarse.</p>
     */
    public boolean canAuthenticate() {
        return this != SUSPENDED;
    }

    public static UserStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("User status cannot be blank");
        }
        try {
            return UserStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown user status: " + value);
        }
    }
}
