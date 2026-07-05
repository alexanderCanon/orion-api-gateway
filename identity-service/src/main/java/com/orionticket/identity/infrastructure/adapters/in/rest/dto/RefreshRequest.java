package com.orionticket.identity.infrastructure.adapters.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(name = "RefreshRequest", description = "Solicitud de refresco de token")
public class RefreshRequest {

    @NotBlank(message = "El refresh token es obligatorio")
    @Schema(description = "Refresh token opaco recibido en el login o en el refresco anterior")
    private String refreshToken;
}
