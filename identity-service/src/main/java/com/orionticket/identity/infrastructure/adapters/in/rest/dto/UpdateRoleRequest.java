package com.orionticket.identity.infrastructure.adapters.in.rest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateRoleRequest {
    @NotNull(message = "El nuevo roleId es obligatorio")
    private UUID newRoleId;
}
