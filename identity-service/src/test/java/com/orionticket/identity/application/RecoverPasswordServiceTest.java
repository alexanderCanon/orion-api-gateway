package com.orionticket.identity.application;

import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.IdentityEventPublisherPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.application.service.RecoverPasswordService;
import com.orionticket.identity.domain.model.OneTimeToken;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.OneTimeTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoverPasswordServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    @Mock
    private OneTimeTokenRepositoryPort oneTimeTokenRepository;
    @Mock
    private RefreshTokenGeneratorPort tokenGenerator;
    @Mock
    private IdentityEventPublisherPort eventPublisher;
    @Mock
    private AuditLogPort auditLogPort;

    private RecoverPasswordService service;

    @BeforeEach
    void setUp() {
        service = new RecoverPasswordService(userRepositoryPort, oneTimeTokenRepository,
                tokenGenerator, eventPublisher, auditLogPort);
        ReflectionTestUtils.setField(service, "recoveryTokenTtlSeconds", 3600L);
        ReflectionTestUtils.setField(service, "recoveryTokenMinIntervalSeconds", 60L);
    }

    @Test
    void givenExistingEmail_whenRequestRecovery_thenGeneratesTokenAndPublishesEvent() {
        String email = "user@example.com";
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email(email)
                .status("ACTIVE")
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(oneTimeTokenRepository.countActiveForUserAndType(user.getUserId(),
                OneTimeToken.TokenType.PASSWORD_RECOVERY)).thenReturn(0L);
        when(tokenGenerator.generate()).thenReturn("raw-token");
        when(tokenGenerator.hash("raw-token")).thenReturn("hash");

        service.requestPasswordRecovery(email);

        verify(oneTimeTokenRepository).save(any(OneTimeToken.class));
        verify(eventPublisher).publishPasswordRecoveryRequested(eq(user), eq("raw-token"));
        verify(auditLogPort).logAction(eq(user.getUserId()), eq("PASSWORD_RECOVERY_REQUESTED"), anyString());
    }

    @Test
    void givenNonExistentEmail_whenRequestRecovery_thenDoesNothingButDoesNotThrow() {
        String email = "nobody@example.com";
        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.requestPasswordRecovery(email));

        verify(oneTimeTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishPasswordRecoveryRequested(any(), any());
    }

    @Test
    void givenActiveTokenAlreadyExists_whenRequestRecovery_thenDoesNotGenerateNewToken() {
        String email = "user@example.com";
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email(email)
                .status("ACTIVE")
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(oneTimeTokenRepository.countActiveForUserAndType(user.getUserId(),
                OneTimeToken.TokenType.PASSWORD_RECOVERY)).thenReturn(1L);

        service.requestPasswordRecovery(email);

        verify(oneTimeTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishPasswordRecoveryRequested(any(), any());
    }
}
