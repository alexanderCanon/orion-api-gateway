package com.orionticket.identity.application;

import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.IdentityEventPublisherPort;
import com.orionticket.identity.application.service.UserManagementService;
import com.orionticket.identity.domain.exception.RoleNotAllowedException;
import com.orionticket.identity.domain.exception.RoleNotFoundException;
import com.orionticket.identity.domain.exception.UserAlreadyExistsException;
import com.orionticket.identity.domain.exception.UserNotFoundException;
import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RefreshTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserManagementServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private AuditLogPort auditLogPort;

    @Mock
    private IdentityEventPublisherPort eventPublisherPort;

    @Mock
    private RefreshTokenRepositoryPort refreshTokenRepositoryPort;

    @Mock
    private RoleRepositoryPort roleRepositoryPort;

    @InjectMocks
    private UserManagementService userManagementService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldSuspendUserSuccessfully() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User existingUser = User.builder()
                .userId(userId)
                .email("test@example.com")
                .status("ACTIVE")
                .build();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepositoryPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User suspendedUser = userManagementService.suspendUser(userId, adminId);

        // Assert
        assertEquals("SUSPENDED", suspendedUser.getStatus());
        verify(userRepositoryPort, times(1)).save(suspendedUser);
        verify(auditLogPort, times(1)).logAction(adminId, "SUSPEND_USER", "User " + userId + " was suspended.");
        // Fase 1: la suspensión debe revocar todas las sesiones activas.
        verify(refreshTokenRepositoryPort, times(1)).revokeAllForUser(userId);
    }

    @Test
    void shouldThrowExceptionWhenSuspendingNonExistentUser() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(UserNotFoundException.class, () -> {
            userManagementService.suspendUser(userId, adminId);
        });

        verify(userRepositoryPort, never()).save(any());
        verify(auditLogPort, never()).logAction(any(), any(), any());
    }

    // --- Fase 5: tests nuevos ---

    @Test
    void createOrganizerStaffRejectsRoleNotInAllowedList() {
        // Fase 5.1: createOrganizerStaff debe validar que el rol sea
        // VENUE_STAFF o DOOR_VALIDATOR por nombre, no por UUID mágico.
        UUID organizerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Role wrongRole = Role.builder().roleId(roleId).name("BUYER").build();

        when(roleRepositoryPort.findById(roleId)).thenReturn(Optional.of(wrongRole));

        assertThrows(RoleNotAllowedException.class, () ->
            userManagementService.createOrganizerStaff(organizerId, "staff@org.com",
                    "hash", "Staff", "123", roleId, creatorId)
        );

        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    void createOrganizerStaffThrowsRoleNotFoundWhenRoleDoesNotExist() {
        // Fase 5.1: si el roleId no existe en la BD, se lanza RoleNotFoundException.
        UUID organizerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        when(roleRepositoryPort.findById(roleId)).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class, () ->
            userManagementService.createOrganizerStaff(organizerId, "staff@org.com",
                    "hash", "Staff", "123", roleId, creatorId)
        );

        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    void createOrganizerStaffAcceptsVenueStaffRole() {
        // Fase 5.1: VENUE_STAFF es un rol permitido para staff de organizador.
        UUID organizerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Role venueStaffRole = Role.builder().roleId(roleId).name("VENUE_STAFF").build();

        when(roleRepositoryPort.findById(roleId)).thenReturn(Optional.of(venueStaffRole));
        when(userRepositoryPort.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepositoryPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User staff = userManagementService.createOrganizerStaff(organizerId, "staff@org.com",
                "hash", "Staff", "123", roleId, creatorId);

        assertEquals(roleId, staff.getRoleId());
        assertEquals(organizerId, staff.getOrganizerId());
        verify(auditLogPort).logAction(eq(creatorId), eq("CREATE_STAFF"), anyString());
    }

    @Test
    void updateUserRoleProhibitsSelfModification() {
        // Fase 5.2: un usuario no puede modificar su propio rol.
        UUID userId = UUID.randomUUID();
        UUID newRoleId = UUID.randomUUID();
        User user = User.builder().userId(userId).status("ACTIVE").build();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(RoleNotAllowedException.class, () ->
            userManagementService.updateUserRole(userId, newRoleId, userId)
        );

        verify(userRepositoryPort, never()).save(any(User.class));
        verify(refreshTokenRepositoryPort, never()).revokeAllForUser(any());
    }

    @Test
    void updateUserRoleThrowsRoleNotFoundWhenRoleDoesNotExist() {
        // Fase 5.2: si el nuevo rol no existe, se lanza RoleNotFoundException (404).
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID newRoleId = UUID.randomUUID();
        User user = User.builder().userId(userId).status("ACTIVE").build();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepositoryPort.findById(newRoleId)).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class, () ->
            userManagementService.updateUserRole(userId, newRoleId, adminId)
        );

        verify(userRepositoryPort, never()).save(any(User.class));
        verify(refreshTokenRepositoryPort, never()).revokeAllForUser(any());
    }

    @Test
    void updateUserRoleSucceedsAndRevokesSessionsWhenRoleExists() {
        // Fase 5.2: cuando el rol existe y no es auto-modificación, el cambio
        // procede y se revocan las sesiones activas.
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID newRoleId = UUID.randomUUID();
        User user = User.builder().userId(userId).status("ACTIVE").build();
        Role newRole = Role.builder().roleId(newRoleId).name("ORGANIZER").build();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepositoryPort.findById(newRoleId)).thenReturn(Optional.of(newRole));
        when(userRepositoryPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userManagementService.updateUserRole(userId, newRoleId, adminId);

        assertEquals(newRoleId, updated.getRoleId());
        verify(refreshTokenRepositoryPort).revokeAllForUser(userId);
        verify(auditLogPort).logAction(eq(adminId), eq("UPDATE_USER_ROLE"), anyString());
    }
}
