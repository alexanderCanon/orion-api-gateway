package com.orionticket.identity.infrastructure.adapters.in.rest.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID userId;
    private String email;
    private String fullName;
    private String status;
    private UUID roleId;
    private UUID organizerId;
}
