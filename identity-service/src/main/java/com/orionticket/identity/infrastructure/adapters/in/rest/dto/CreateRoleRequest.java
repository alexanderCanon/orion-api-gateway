package com.orionticket.identity.infrastructure.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateRoleRequest {
    @NotBlank(message = "El nombre del rol es obligatorio")
    private String name;
    
    private List<String> permissions;
}
