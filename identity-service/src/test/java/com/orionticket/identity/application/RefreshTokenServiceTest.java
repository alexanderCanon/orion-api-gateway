package com.orionticket.identity.application;

import com.orionticket.identity.application.port.in.AuthResult;
import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.JwtProviderPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.application.service.RefreshTokenService;
import com.orionticket.identity.domain.exception.AccountDisabledException;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.model.RefreshToken;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RefreshTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepositoryPort refreshTokenRepository;
    @Mock
    private UserRepositoryPort userRepositoryPort;
    @Mock
    private JwtProviderPort jwtProviderPort;
    @Mock
    private RefreshTokenGeneratorPort refreshTokenGenerator;
    @Mock
    private AuditLogPort auditLogPort;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository, userRepositoryPort,
                jwtProviderPort, refreshTokenGenerator, auditLogPort);
        ReflectionTestUtils.setField(service, "accessExpirationSeconds", 900L);
        ReflectionTestUtils.setField(service, "refreshExpirationSeconds", 2592000L);
    }

    @Test
    void givenValidRefreshToken_whenRefresh_thenRotatesAndReturnsNewPair() {
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .tokenId(tokenId)
                .userId(userId)
                .tokenHash("hash")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .revokedAt(null)
                .build();
        User user = User.builder().userId(userId).status("ACTIVE").build();

        when(refreshTokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(jwtProviderPort.generateToken(user)).thenReturn("new-access");
        when(refreshTokenGenerator.generate()).thenReturn("new-raw");
        when(refreshTokenGenerator.hash("new-raw")).thenReturn("new-hash");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AuthResult result = service.refresh("raw", "UA", "127.0.0.1");

        assertEquals("new-access", result.accessToken());
        assertEquals("new-raw", result.refreshToken());
        // El token viejo se revoca y se guarda, y el nuevo se guarda: 2 saves.
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
        // El nuevo token encadena con el viejo vía parentId.
        verify(refreshTokenRepository).save(argThat(t ->
                t.getParentId() != null && t.getParentId().equals(tokenId)));
    }

    @Test
    void givenUnknownRefreshToken_whenRefresh_thenThrowsInvalidCredentials() {
        when(refreshTokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> service.refresh("raw", "UA", "127.0.0.1"));
        verify(refreshTokenRepository, never()).revokeChain(any());
        verify(jwtProviderPort, never()).generateToken(any());
    }

    @Test
    void givenAlreadyRevokedRefreshToken_whenRefresh_thenRevokesChainAndThrows() {
        // DETECCIÓN DE REUSO: un token ya revocado (ya rotado) presentado de
        // nuevo → se revoca toda la cadena y se rechaza.
        UUID tokenId = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .tokenId(tokenId)
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .revokedAt(Instant.now().minusSeconds(60)) // ya revocado
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(refreshTokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));

        assertThrows(InvalidCredentialsException.class,
                () -> service.refresh("raw", "UA", "127.0.0.1"));
        verify(refreshTokenRepository).revokeChain(tokenId);
        verify(jwtProviderPort, never()).generateToken(any());
    }

    @Test
    void givenExpiredRefreshToken_whenRefresh_thenRevokesAndThrows() {
        UUID tokenId = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .tokenId(tokenId)
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .expiresAt(Instant.now().minusSeconds(60)) // expirado
                .revokedAt(null)
                .build();

        when(refreshTokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThrows(InvalidCredentialsException.class,
                () -> service.refresh("raw", "UA", "127.0.0.1"));
        // Se persiste el token marcado como revocado.
        verify(refreshTokenRepository).save(argThat(RefreshToken::isRevoked));
        verify(jwtProviderPort, never()).generateToken(any());
    }

    @Test
    void givenSuspendedUser_whenRefresh_thenRevokesAllAndThrowsAccountDisabled() {
        UUID userId = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .tokenId(UUID.randomUUID())
                .userId(userId)
                .tokenHash("hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revokedAt(null)
                .build();
        User user = User.builder().userId(userId).status("SUSPENDED").build();

        when(refreshTokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(AccountDisabledException.class,
                () -> service.refresh("raw", "UA", "127.0.0.1"));
        // Se revocan TODOS los tokens del usuario suspendido.
        verify(refreshTokenRepository).revokeAllForUser(userId);
        verify(jwtProviderPort, never()).generateToken(any());
    }

    @Test
    void givenBlankRefreshToken_whenRefresh_thenThrowsInvalidCredentials() {
        assertThrows(InvalidCredentialsException.class,
                () -> service.refresh("", "UA", "127.0.0.1"));
        assertThrows(InvalidCredentialsException.class,
                () -> service.refresh(null, "UA", "127.0.0.1"));
        verifyNoInteractions(refreshTokenRepository);
    }
}
