package com.orionticket.identity.infrastructure.adapters.in.rest.dto;

import com.orionticket.identity.domain.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Solicitud para actualizar el estado de un usuario.
 *
 * <p>Reemplaza al endpoint anterior que aceptaba un {@code Map<String,String>}
 * genérico y no persistía el cambio. El estado se valida contra el enum
 * {@link UserStatus} para evitar valores arbitrarios.</p>
 */
@Schema(name = "UpdateStatusRequest", description = "Nuevo estado de la cuenta de usuario")
@Data
public class UpdateStatusRequest {

    @NotNull(message = "El estado es obligatorio")
    @Schema(description = "Nuevo estado", example = "ACTIVE", allowableValues = {"ACTIVE", "SUSPENDED", "UNVERIFIED"})
    private UserStatus status;
}
