package com.orionticket.identity.application;

import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.application.service.ResetPasswordService;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.model.OneTimeToken;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.OneTimeTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.RefreshTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResetPasswordServiceTest {

    @Mock
    private OneTimeTokenRepositoryPort oneTimeTokenRepository;
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

    private ResetPasswordService service;

    @BeforeEach
    void setUp() {
        service = new ResetPasswordService(oneTimeTokenRepository, userRepositoryPort,
                passwordHasherPort, refreshTokenRepository, tokenGenerator, auditLogPort);
    }

    @Test
    void givenValidToken_whenResetPassword_thenUpdatesPasswordAndRevokesSessions() {
        UUID userId = UUID.randomUUID();
        String rawToken = "raw-token";
        String tokenHash = "hash";
        String newPassword = "newPassword123";

        OneTimeToken token = OneTimeToken.builder()
                .tokenId(UUID.randomUUID())
                .userId(userId)
                .tokenHash(tokenHash)
                .tokenType(OneTimeToken.TokenType.PASSWORD_RECOVERY)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .usedAt(null)
                .build();

        User user = User.builder()
                .userId(userId)
                .email("user@example.com")
                .passwordHash("old-hash")
                .status("ACTIVE")
                .build();

        when(tokenGenerator.hash(rawToken)).thenReturn(tokenHash);
        when(oneTimeTokenRepository.findByTokenHashAndType(tokenHash,
                OneTimeToken.TokenType.PASSWORD_RECOVERY)).thenReturn(Optional.of(token));
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasherPort.hash(newPassword)).thenReturn("new-hash");

        service.resetPassword(rawToken, newPassword);

        assertEquals("new-hash", user.getPasswordHash());
        verify(userRepositoryPort).save(user);
        verify(oneTimeTokenRepository).save(token);
        assertTrue(token.isUsed());
        verify(oneTimeTokenRepository).markAllUnusedForUserAndType(userId,
                OneTimeToken.TokenType.PASSWORD_RECOVERY);
        verify(refreshTokenRepository).revokeAllForUser(userId);
        verify(auditLogPort).logAction(eq(userId), eq("PASSWORD_RECOVERED"), anyString());
    }

    @Test
    void givenNonExistentToken_whenResetPassword_thenThrowsInvalidCredentials() {
        when(tokenGenerator.hash("bad-token")).thenReturn("bad-hash");
        when(oneTimeTokenRepository.findByTokenHashAndType("bad-hash",
                OneTimeToken.TokenType.PASSWORD_RECOVERY)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> service.resetPassword("bad-token", "newPassword123"));

        verify(userRepositoryPort, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllForUser(any());
    }

    @Test
    void givenExpiredToken_whenResetPassword_thenThrowsInvalidCredentials() {
        OneTimeToken token = OneTimeToken.builder()
                .tokenId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .tokenType(OneTimeToken.TokenType.PASSWORD_RECOVERY)
                .createdAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600))
                .usedAt(null)
                .build();

        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(oneTimeTokenRepository.findByTokenHashAndType("hash",
                OneTimeToken.TokenType.PASSWORD_RECOVERY)).thenReturn(Optional.of(token));

        assertThrows(InvalidCredentialsException.class,
                () -> service.resetPassword("raw", "newPassword123"));
    }

    @Test
    void givenAlreadyUsedToken_whenResetPassword_thenThrowsInvalidCredentials() {
        OneTimeToken token = OneTimeToken.builder()
                .tokenId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .tokenType(OneTimeToken.TokenType.PASSWORD_RECOVERY)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .usedAt(Instant.now().minusSeconds(30))
                .build();

        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(oneTimeTokenRepository.findByTokenHashAndType("hash",
                OneTimeToken.TokenType.PASSWORD_RECOVERY)).thenReturn(Optional.of(token));

        assertThrows(InvalidCredentialsException.class,
                () -> service.resetPassword("raw", "newPassword123"));
    }

    @Test
    void givenBlankToken_whenResetPassword_thenThrowsInvalidCredentials() {
        assertThrows(InvalidCredentialsException.class,
                () -> service.resetPassword("", "newPassword123"));
        assertThrows(InvalidCredentialsException.class,
                () -> service.resetPassword(null, "newPassword123"));
    }
}
