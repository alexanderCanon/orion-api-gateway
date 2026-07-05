package com.orionticket.identity.infrastructure.adapters.in.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RecoverRequest {

    @Email(message = "Debe ser un email válido")
    @NotBlank(message = "El email no puede estar vacío")
    @Size(max = 255, message = "El email no puede exceder 255 caracteres")
    private String email;
}
