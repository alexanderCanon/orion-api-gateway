package com.orionticket.identity.infrastructure.adapters.in.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateUserRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String password;

    @NotBlank
    @Size(max = 255)
    private String fullName;

    @Size(max = 50)
    @Pattern(regexp = "^$|^[+0-9][0-9\\s\\-()]{6,49}$",
            message = "El teléfono solo puede contener dígitos, espacios, guiones, paréntesis y un prefijo '+'")
    private String phone;

    @NotNull
    private UUID roleId;

    private UUID organizerId;
}
