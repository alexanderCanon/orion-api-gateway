package com.orionticket.identity.application;

import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.application.service.ChangePasswordService;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.exception.UserNotFoundException;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RefreshTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangePasswordServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    @Mock
    private PasswordHasherPort passwordHasherPort;
    @Mock
    private RefreshTokenRepositoryPort refreshTokenRepository;
    @Mock
    private RefreshTokenGeneratorPort tokenGenerator;
    @Mock
    private AuditLogPort auditLogPort;

    private ChangePasswordService service;

    @BeforeEach
    void setUp() {
        service = new ChangePasswordService(userRepositoryPort, passwordHasherPort,
                refreshTokenRepository, tokenGenerator, auditLogPort);
    }

    @Test
    void givenCorrectCurrentPassword_whenChangePassword_thenUpdatesAndRevokesSessions() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .userId(userId)
                .email("user@example.com")
                .passwordHash("old-hash")
                .status("ACTIVE")
                .build();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches("currentPass", "old-hash")).thenReturn(true);
        when(passwordHasherPort.hash("newPass123")).thenReturn("new-hash");

        service.changePassword(userId, "currentPass", "newPass123", "current-refresh");

        assertEquals("new-hash", user.getPasswordHash());
        verify(userRepositoryPort).save(user);
        verify(refreshTokenRepository).revokeAllForUser(userId);
        verify(auditLogPort).logAction(eq(userId), eq("PASSWORD_CHANGED"), anyString());
    }

    @Test
    void givenIncorrectCurrentPassword_whenChangePassword_thenThrowsInvalidCredentials() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .userId(userId)
                .email("user@example.com")
                .passwordHash("old-hash")
                .status("ACTIVE")
                .build();

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches("wrongPass", "old-hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> service.changePassword(userId, "wrongPass", "newPass123", "refresh"));

        verify(userRepositoryPort, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllForUser(any());
    }

    @Test
    void givenNonExistentUser_whenChangePassword_thenThrowsUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> service.changePassword(userId, "current", "new", "refresh"));
    }
}
