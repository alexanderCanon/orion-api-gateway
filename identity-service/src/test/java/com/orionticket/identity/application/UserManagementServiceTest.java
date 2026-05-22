package com.orionticket.identity.application;

import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.service.UserManagementService;
import com.orionticket.identity.domain.exception.UserNotFoundException;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
}
