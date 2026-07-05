package com.orionticket.identity.application;

import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.application.service.VerifyEmailService;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.model.OneTimeToken;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.OneTimeTokenRepositoryPort;
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
class VerifyEmailServiceTest {

    @Mock
    private OneTimeTokenRepositoryPort oneTimeTokenRepository;
    @Mock
    private UserRepositoryPort userRepositoryPort;
    @Mock
    private RefreshTokenGeneratorPort tokenGenerator;
    @Mock
    private AuditLogPort auditLogPort;

    private VerifyEmailService service;

    @BeforeEach
    void setUp() {
        service = new VerifyEmailService(oneTimeTokenRepository, userRepositoryPort,
                tokenGenerator, auditLogPort);
    }

    @Test
    void givenValidToken_whenVerifyEmail_thenTransitionsToActive() {
        UUID userId = UUID.randomUUID();
        OneTimeToken token = OneTimeToken.builder()
                .tokenId(UUID.randomUUID())
                .userId(userId)
                .tokenHash("hash")
                .tokenType(OneTimeToken.TokenType.EMAIL_VERIFICATION)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(86400))
                .usedAt(null)
                .build();

        User user = User.builder()
                .userId(userId)
                .email("user@example.com")
                .status("UNVERIFIED")
                .build();

        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(oneTimeTokenRepository.findByTokenHashAndType("hash",
                OneTimeToken.TokenType.EMAIL_VERIFICATION)).thenReturn(Optional.of(token));
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));

        service.verifyEmail("raw");

        assertEquals("ACTIVE", user.getStatus());
        assertTrue(token.isUsed());
        verify(userRepositoryPort).save(user);
        verify(oneTimeTokenRepository).save(token);
        verify(oneTimeTokenRepository).markAllUnusedForUserAndType(userId,
                OneTimeToken.TokenType.EMAIL_VERIFICATION);
        verify(auditLogPort).logAction(eq(userId), eq("EMAIL_VERIFIED"), anyString());
    }

    @Test
    void givenNonExistentToken_whenVerifyEmail_thenThrowsInvalidCredentials() {
        when(tokenGenerator.hash("bad")).thenReturn("bad-hash");
        when(oneTimeTokenRepository.findByTokenHashAndType("bad-hash",
                OneTimeToken.TokenType.EMAIL_VERIFICATION)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> service.verifyEmail("bad"));
        verify(userRepositoryPort, never()).save(any());
    }

    @Test
    void givenExpiredToken_whenVerifyEmail_thenThrowsInvalidCredentials() {
        OneTimeToken token = OneTimeToken.builder()
                .tokenId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .tokenType(OneTimeToken.TokenType.EMAIL_VERIFICATION)
                .createdAt(Instant.now().minusSeconds(100000))
                .expiresAt(Instant.now().minusSeconds(3600))
                .usedAt(null)
                .build();

        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(oneTimeTokenRepository.findByTokenHashAndType("hash",
                OneTimeToken.TokenType.EMAIL_VERIFICATION)).thenReturn(Optional.of(token));

        assertThrows(InvalidCredentialsException.class, () -> service.verifyEmail("raw"));
    }

    @Test
    void givenAlreadyUsedToken_whenVerifyEmail_thenThrowsInvalidCredentials() {
        OneTimeToken token = OneTimeToken.builder()
                .tokenId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .tokenType(OneTimeToken.TokenType.EMAIL_VERIFICATION)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(86400))
                .usedAt(Instant.now().minusSeconds(30))
                .build();

        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(oneTimeTokenRepository.findByTokenHashAndType("hash",
                OneTimeToken.TokenType.EMAIL_VERIFICATION)).thenReturn(Optional.of(token));

        assertThrows(InvalidCredentialsException.class, () -> service.verifyEmail("raw"));
    }

    @Test
    void givenBlankToken_whenVerifyEmail_thenThrowsInvalidCredentials() {
        assertThrows(InvalidCredentialsException.class, () -> service.verifyEmail(""));
        assertThrows(InvalidCredentialsException.class, () -> service.verifyEmail(null));
    }
}
