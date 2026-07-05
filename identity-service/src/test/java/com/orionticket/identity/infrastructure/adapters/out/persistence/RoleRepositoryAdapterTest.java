package com.orionticket.identity.infrastructure.adapters.out.persistence;

import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.RoleJpaEntity;
import com.orionticket.identity.infrastructure.adapters.out.persistence.mapper.RoleMapper;
import com.orionticket.identity.infrastructure.adapters.out.persistence.repository.SpringDataRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleRepositoryAdapterTest {

    @Mock
    private SpringDataRoleRepository roleRepository;

    @InjectMocks
    private RoleRepositoryAdapter adapter;

    @Test
    void savePersistsAndReturnsDomainRole() {
        Role role = Role.builder()
                .roleId(UUID.randomUUID())
                .name("ADMIN")
                .permissions(List.of("read"))
                .build();
        RoleJpaEntity entity = RoleMapper.toEntity(role);

        when(roleRepository.save(any(RoleJpaEntity.class))).thenReturn(entity);

        Role result = adapter.save(role);

        assertNotNull(result);
        assertEquals(role.getRoleId(), result.getRoleId());
        assertEquals("ADMIN", result.getName());
        verify(roleRepository).save(any(RoleJpaEntity.class));
    }

    @Test
    void findByIdReturnsDomainWhenFound() {
        UUID roleId = UUID.randomUUID();
        RoleJpaEntity entity = new RoleJpaEntity();
        entity.setRoleId(roleId);
        entity.setName("BUYER");
        entity.setPermissions(List.of());

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(entity));

        Optional<Role> result = adapter.findById(roleId);

        assertTrue(result.isPresent());
        assertEquals("BUYER", result.get().getName());
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        UUID roleId = UUID.randomUUID();
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        Optional<Role> result = adapter.findById(roleId);

        assertTrue(result.isEmpty());
    }

    @Test
    void findAllReturnsAllRolesAsDomain() {
        RoleJpaEntity e1 = new RoleJpaEntity();
        e1.setRoleId(UUID.randomUUID());
        e1.setName("ADMIN");
        e1.setPermissions(List.of());
        RoleJpaEntity e2 = new RoleJpaEntity();
        e2.setRoleId(UUID.randomUUID());
        e2.setName("BUYER");
        e2.setPermissions(List.of());

        when(roleRepository.findAll()).thenReturn(List.of(e1, e2));

        List<Role> result = adapter.findAll();

        assertEquals(2, result.size());
        assertEquals("ADMIN", result.get(0).getName());
        assertEquals("BUYER", result.get(1).getName());
    }

    @Test
    void deleteByIdDelegatesToRepository() {
        UUID roleId = UUID.randomUUID();

        adapter.deleteById(roleId);

        verify(roleRepository).deleteById(roleId);
    }
}
