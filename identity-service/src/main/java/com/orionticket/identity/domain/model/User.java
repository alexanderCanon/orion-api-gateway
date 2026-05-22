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
                .status("UNVERIFIED") // Cumple con el criterio de aceptación de US-001
                .roleId(defaultBuyerRoleId)
                .createdAt(ZonedDateTime.now())
                .build();
    }

    public void suspend() {
        this.status = "SUSPENDED";
    }
}
