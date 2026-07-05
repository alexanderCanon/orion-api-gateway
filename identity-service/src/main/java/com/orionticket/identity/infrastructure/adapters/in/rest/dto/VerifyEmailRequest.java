package com.orionticket.identity.infrastructure.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VerifyEmailRequest {

    @NotBlank(message = "El token no puede estar vacío")
    private String token;
}
