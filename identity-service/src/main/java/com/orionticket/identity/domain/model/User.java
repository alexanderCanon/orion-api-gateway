package com.orionticket.identity.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class User {
    /** Umbral de intentos fallidos consecutivos que dispara el lockout. */
    public static final int LOCKOUT_THRESHOLD = 5;

    private UUID userId;
    private String email;
    private String passwordHash;
    private String fullName;
    private String phone;
    private String status;
    private UUID roleId;
    private UUID organizerId;
    private ZonedDateTime createdAt;
    private int failedLoginAttempts;
    private Instant lockedUntil;

    /**
     * Regla de Negocio: Un nuevo comprador siempre inicia como UNVERIFIED.
     */
    public static User createBuyer(String email, String passwordHash, String fullName, String phone, UUID defaultBuyerRoleId) {
        return User.builder()
                .userId(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordHash)
                .fullName(fullName)
                .phone(phone)
                .status(UserStatus.UNVERIFIED.name()) // Cumple con el criterio de aceptación de US-001
                .roleId(defaultBuyerRoleId)
                .createdAt(ZonedDateTime.now())
                .build();
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED.name();
    }

    /**
     * Transición de dominio: activa una cuenta (p. ej. al verificar email
     * o al ser aprobada por un administrador).
     *
     * @throws IllegalStateException si la cuenta está suspendida; la
     *         reactivación de cuentas suspendidas requiere un flujo
     *         administrativo explícito, no este método.
     */
    public void activate() {
        if (UserStatus.SUSPENDED.name().equals(this.status)) {
            throw new IllegalStateException(
                    "Cannot activate a suspended account directly; use the reactivation flow.");
        }
        this.status = UserStatus.ACTIVE.name();
    }

    /**
     * Transición de dominio: marca el email como verificado.
     * Solo es válida desde {@code UNVERIFIED}; idempotente si ya está activa.
     */
    public void verifyEmail() {
        if (UserStatus.SUSPENDED.name().equals(this.status)) {
            throw new IllegalStateException("Cannot verify email of a suspended account.");
        }
        this.status = UserStatus.ACTIVE.name();
    }

    /**
     * Indica si el usuario puede autenticarse según su estado actual.
     * Política: {@code SUSPENDED} nunca; {@code ACTIVE} y {@code UNVERIFIED} sí.
     */
    public boolean canAuthenticate() {
        return currentStatus().canAuthenticate();
    }

    public boolean isActive() {
        return UserStatus.ACTIVE.name().equals(this.status);
    }

    public boolean isSuspended() {
        return UserStatus.SUSPENDED.name().equals(this.status);
    }

    // --- Lockout por fuerza bruta (Fase 2, C4) ---

    /**
     * Indica si la cuenta está temporalmente bloqueada por intentos fallidos.
     */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /**
     * Registra un intento de login fallido. Incrementa el contador y, si se
     * alcanza un múltiplo del umbral ({@value #LOCKOUT_THRESHOLD}), fija
     * {@code lockedUntil} con backoff progresivo:
     * <ul>
     *   <li>1.er bloqueo (5 fallos) → 15 min</li>
     *   <li>2.º bloqueo (10 fallos) → 1 h</li>
     *   <li>3.º y siguientes (15+) → 24 h</li>
     * </ul>
     *
     * @return {@code true} si este intento disparó un nuevo bloqueo.
     */
    public boolean registerFailedLogin() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts % LOCKOUT_THRESHOLD == 0) {
            this.lockedUntil = Instant.now().plusSeconds(lockDurationSeconds());
            return true;
        }
        return false;
    }

    /**
     * Resetea el contador de intentos fallidos y limpia el lockout.
     * Se llama tras un login exitoso.
     */
    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /**
     * Devuelve cuántos segundos faltan para que expire el lockout actual.
     * Si no está bloqueada, devuelve 0.
     */
    public long remainingLockSeconds() {
        if (!isLocked()) {
            return 0L;
        }
        return Math.max(0L, lockedUntil.getEpochSecond() - Instant.now().getEpochSecond());
    }

    /**
     * Calcula la duración del bloqueo según el tier (backoff progresivo).
     */
    private long lockDurationSeconds() {
        int tier = this.failedLoginAttempts / LOCKOUT_THRESHOLD;
        return switch (tier) {
            case 1 -> 15 * 60L;          // 15 min
            case 2 -> 60 * 60L;          // 1 h
            default -> 24 * 60 * 60L;    // 24 h
        };
    }

    private UserStatus currentStatus() {
        return UserStatus.fromString(this.status);
    }
}
