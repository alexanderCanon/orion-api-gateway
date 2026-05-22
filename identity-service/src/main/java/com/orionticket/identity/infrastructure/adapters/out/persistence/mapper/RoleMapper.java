package com.orionticket.identity.infrastructure.adapters.out.persistence.mapper;

import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.RoleJpaEntity;

import java.util.ArrayList;

public class RoleMapper {
    public static Role toDomain(RoleJpaEntity entity) {
        if (entity == null) return null;
        return Role.builder()
                .roleId(entity.getRoleId())
                .name(entity.getName())
                .permissions(entity.getPermissions() != null ? new ArrayList<>(entity.getPermissions()) : new ArrayList<>())
                .build();
    }

    public static RoleJpaEntity toEntity(Role role) {
        if (role == null) return null;
        RoleJpaEntity entity = new RoleJpaEntity();
        entity.setRoleId(role.getRoleId());
        entity.setName(role.getName());
        entity.setPermissions(role.getPermissions() != null ? new ArrayList<>(role.getPermissions()) : new ArrayList<>());
        return entity;
    }
}
