package com.orionticket.identity.infrastructure.adapters.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(name = "LogoutRequest", description = "Solicitud de logout")
public class LogoutRequest {

    @NotBlank(message = "El refresh token es obligatorio")
    @Schema(description = "Refresh token a revocar")
    private String refreshToken;

    @Schema(description = "Si es true, revoca todos los refresh tokens del usuario (logout de todas las sesiones)",
            example = "false")
    private boolean all;
}
