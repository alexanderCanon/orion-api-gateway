package com.orionticket.identity.infrastructure.adapters.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@Schema(name = "LoginResponse", description = "Tokens de acceso y refresco tras autenticación exitosa")
public class LoginResponse {

    @Schema(description = "Access token JWT de corta vida")
    private String accessToken;

    @Schema(description = "Refresh token opaco rotativo")
    private String refreshToken;

    @Schema(description = "Tipo de token", example = "Bearer")
    private String tokenType;

    @Schema(description = "Vida útil del access token en segundos", example = "900")
    private long expiresIn;

    @Schema(description = "Identificador del usuario")
    private UUID userId;

    @Schema(description = "Nombre del rol del usuario", example = "BUYER")
    private String role;

    @Schema(description = "Identificador del organizador (si aplica)")
    private UUID organizerId;
}
