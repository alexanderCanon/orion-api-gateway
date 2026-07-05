package com.orionticket.identity.infrastructure.adapters.out.persistence.mapper;

import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.RoleJpaEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoleMapperTest {

    @Test
    void toDomainMapsAllFields() {
        UUID roleId = UUID.randomUUID();
        RoleJpaEntity entity = new RoleJpaEntity();
        entity.setRoleId(roleId);
        entity.setName("ADMIN");
        entity.setPermissions(List.of("read", "write"));

        Role domain = RoleMapper.toDomain(entity);

        assertEquals(roleId, domain.getRoleId());
        assertEquals("ADMIN", domain.getName());
        assertEquals(List.of("read", "write"), domain.getPermissions());
    }

    @Test
    void toDomainHandlesNullPermissions() {
        RoleJpaEntity entity = new RoleJpaEntity();
        entity.setRoleId(UUID.randomUUID());
        entity.setName("GUEST");
        entity.setPermissions(null);

        Role domain = RoleMapper.toDomain(entity);

        assertNotNull(domain.getPermissions());
        assertTrue(domain.getPermissions().isEmpty());
    }

    @Test
    void toDomainNullReturnsNull() {
        assertNull(RoleMapper.toDomain(null));
    }

    @Test
    void toEntityMapsAllFields() {
        UUID roleId = UUID.randomUUID();
        Role role = Role.builder()
                .roleId(roleId)
                .name("EDITOR")
                .permissions(List.of("edit", "publish"))
                .build();

        RoleJpaEntity entity = RoleMapper.toEntity(role);

        assertEquals(roleId, entity.getRoleId());
        assertEquals("EDITOR", entity.getName());
        assertEquals(List.of("edit", "publish"), entity.getPermissions());
    }

    @Test
    void toEntityHandlesNullPermissions() {
        Role role = Role.builder()
                .roleId(UUID.randomUUID())
                .name("VIEWER")
                .permissions(null)
                .build();

        RoleJpaEntity entity = RoleMapper.toEntity(role);

        assertNotNull(entity.getPermissions());
        assertTrue(entity.getPermissions().isEmpty());
    }

    @Test
    void toEntityNullReturnsNull() {
        assertNull(RoleMapper.toEntity(null));
    }
}
