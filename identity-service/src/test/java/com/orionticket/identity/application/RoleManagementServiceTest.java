package com.orionticket.identity.application;

import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.service.RoleManagementService;
import com.orionticket.identity.domain.exception.RoleNotFoundException;
import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleManagementServiceTest {

    @Mock
    private RoleRepositoryPort roleRepositoryPort;

    @Mock
    private AuditLogPort auditLogPort;

    @InjectMocks
    private RoleManagementService roleManagementService;

    @Test
    void createRolePersistsAndAudits() {
        UUID adminId = UUID.randomUUID();
        Role newRole = Role.builder()
                .roleId(UUID.randomUUID())
                .name("STAFF")
                .permissions(List.of("events:read"))
                .build();

        when(roleRepositoryPort.save(any(Role.class))).thenReturn(newRole);

        Role result = roleManagementService.createRole("STAFF", List.of("events:read"), adminId);

        assertEquals("STAFF", result.getName());
        verify(roleRepositoryPort).save(any(Role.class));
        verify(auditLogPort).logAction(eq(adminId), eq("CREATE_ROLE"), anyString());
    }

    @Test
    void updateRoleModifiesAndAudits() {
        UUID roleId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Role existing = Role.builder()
                .roleId(roleId)
                .name("OLD")
                .permissions(List.of())
                .build();

        when(roleRepositoryPort.findById(roleId)).thenReturn(Optional.of(existing));
        when(roleRepositoryPort.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        Role result = roleManagementService.updateRole(roleId, "NEW", List.of("events:write"), adminId);

        assertEquals("NEW", result.getName());
        assertEquals(List.of("events:write"), result.getPermissions());
        verify(auditLogPort).logAction(eq(adminId), eq("UPDATE_ROLE"), anyString());
    }

    @Test
    void updateRoleThrowsWhenNotFound() {
        UUID roleId = UUID.randomUUID();
        when(roleRepositoryPort.findById(roleId)).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class,
                () -> roleManagementService.updateRole(roleId, "X", List.of(), UUID.randomUUID()));
    }

    @Test
    void deleteRoleRemovesAndAudits() {
        UUID roleId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Role existing = Role.builder().roleId(roleId).name("TEMP").permissions(List.of()).build();

        when(roleRepositoryPort.findById(roleId)).thenReturn(Optional.of(existing));

        roleManagementService.deleteRole(roleId, adminId);

        verify(roleRepositoryPort).deleteById(roleId);
        verify(auditLogPort).logAction(eq(adminId), eq("DELETE_ROLE"), anyString());
    }

    @Test
    void deleteRoleThrowsWhenNotFound() {
        UUID roleId = UUID.randomUUID();
        when(roleRepositoryPort.findById(roleId)).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class,
                () -> roleManagementService.deleteRole(roleId, UUID.randomUUID()));
    }

    @Test
    void getAllRolesReturnsAll() {
        List<Role> roles = List.of(
                Role.builder().roleId(UUID.randomUUID()).name("A").permissions(List.of()).build(),
                Role.builder().roleId(UUID.randomUUID()).name("B").permissions(List.of()).build()
        );
        when(roleRepositoryPort.findAll()).thenReturn(roles);

        List<Role> result = roleManagementService.getAllRoles();

        assertEquals(2, result.size());
        assertEquals("A", result.get(0).getName());
        assertEquals("B", result.get(1).getName());
    }

    @Test
    void createRoleWithDuplicateNameThrowsDataIntegrityViolation() {
        // Fase 4: dos requests concurrentes con el mismo nombre de rol pueden
        // pasar el check-then-act y explotar en la BD. El servicio debe
        // traducir la DataIntegrityViolationException a un mensaje claro.
        UUID adminId = UUID.randomUUID();
        when(roleRepositoryPort.save(any(Role.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate role name"));

        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> roleManagementService.createRole("DUPLICATE", List.of("events:read"), adminId));

        assertTrue(ex.getMessage().contains("DUPLICATE"));
        verify(auditLogPort, never()).logAction(any(), any(), any());
    }

    @Test
    void deleteRoleThrowsRoleNotFoundExceptionWhenNotFound() {
        // Fase 4: antes se lanzaba RuntimeException genérico; ahora RoleNotFoundException.
        UUID roleId = UUID.randomUUID();
        when(roleRepositoryPort.findById(roleId)).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class,
                () -> roleManagementService.deleteRole(roleId, UUID.randomUUID()));
    }
}
