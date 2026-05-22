package com.orionticket.identity.domain.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class Role {
    private UUID roleId;
    private String name;
    private List<String> permissions;
}
