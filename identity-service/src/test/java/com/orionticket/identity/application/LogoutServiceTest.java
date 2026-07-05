package com.orionticket.identity.application;

import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.application.service.LogoutService;
import com.orionticket.identity.domain.model.RefreshToken;
import com.orionticket.identity.domain.port.out.RefreshTokenRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    @Mock
    private RefreshTokenRepositoryPort refreshTokenRepository;
    @Mock
    private RefreshTokenGeneratorPort refreshTokenGenerator;
    @Mock
    private AuditLogPort auditLogPort;

    private LogoutService service;

    @BeforeEach
    void setUp() {
        service = new LogoutService(refreshTokenRepository, refreshTokenGenerator, auditLogPort);
    }

    @Test
    void givenValidToken_whenLogout_thenRevokesSingleToken() {
        UUID userId = UUID.randomUUID();
        RefreshToken token = RefreshToken.builder()
                .tokenId(UUID.randomUUID())
                .userId(userId)
                .tokenHash("hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revokedAt(null)
                .build();

        when(refreshTokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.logout("raw", false);

        verify(refreshTokenRepository).save(argThat(RefreshToken::isRevoked));
        verify(refreshTokenRepository, never()).revokeAllForUser(any());
        // Fase 4: logout individual debe auditar LOGOUT.
        verify(auditLogPort).logAction(eq(userId), eq("LOGOUT"), anyString());
    }

    @Test
    void givenValidTokenAndAllTrue_whenLogout_thenRevokesAllUserTokens() {
        UUID userId = UUID.randomUUID();
        RefreshToken token = RefreshToken.builder()
                .tokenId(UUID.randomUUID())
                .userId(userId)
                .tokenHash("hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revokedAt(null)
                .build();

        when(refreshTokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        service.logout("raw", true);

        verify(refreshTokenRepository).revokeAllForUser(userId);
        verify(refreshTokenRepository, never()).save(any());
        // Fase 4: logout all debe auditar LOGOUT_ALL.
        verify(auditLogPort).logAction(eq(userId), eq("LOGOUT_ALL"), anyString());
    }

    @Test
    void givenUnknownToken_whenLogout_thenIsIdempotentAndDoesNotFail() {
        when(refreshTokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

        // No debe lanzar excepción: logout de token desconocido es idempotente.
        service.logout("raw", false);

        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllForUser(any());
    }

    @Test
    void givenBlankToken_whenLogout_thenDoesNothing() {
        service.logout("", false);
        service.logout(null, false);
        verifyNoInteractions(refreshTokenRepository);
    }
}
