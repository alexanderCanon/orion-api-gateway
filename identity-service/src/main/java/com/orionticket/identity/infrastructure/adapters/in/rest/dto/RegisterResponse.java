package com.orionticket.identity.infrastructure.adapters.in.rest.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RegisterResponse {
    private UUID userId;
    private String email;
    private String status; // Ej: UNVERIFIED
}
