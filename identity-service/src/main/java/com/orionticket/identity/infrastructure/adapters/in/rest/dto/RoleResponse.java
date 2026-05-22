package com.orionticket.identity.infrastructure.adapters.in.rest.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RoleResponse {
    private UUID roleId;
    private String name;
    private List<String> permissions;
}
