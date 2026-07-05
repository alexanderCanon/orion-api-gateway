package com.orionticket.identity.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class User {
    private UUID userId;
    private String email;
    private String passwordHash;
    private String fullName;
    private String phone;
    private String status;
    private UUID roleId;
    private UUID organizerId;
    private ZonedDateTime createdAt;

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

    private UserStatus currentStatus() {
        return UserStatus.fromString(this.status);
    }
}
