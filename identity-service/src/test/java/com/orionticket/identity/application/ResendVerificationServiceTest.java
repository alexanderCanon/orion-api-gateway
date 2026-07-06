package com.orionticket.identity.application;

import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.IdentityEventPublisherPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.application.service.ResendVerificationService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResendVerificationServiceTest {

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

    private ResendVerificationService service;

    @BeforeEach
    void setUp() {
        service = new ResendVerificationService(userRepositoryPort, oneTimeTokenRepository,
                tokenGenerator, eventPublisher, auditLogPort);
        ReflectionTestUtils.setField(service, "verificationTokenTtlSeconds", 86400L);
    }

    @Test
    void givenUnverifiedUser_whenResend_thenGeneratesTokenAndPublishesEvent() {
        String email = "unverified@example.com";
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email(email)
                .status("UNVERIFIED")
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(oneTimeTokenRepository.countActiveForUserAndType(user.getUserId(),
                OneTimeToken.TokenType.EMAIL_VERIFICATION)).thenReturn(0L);
        when(tokenGenerator.generate()).thenReturn("raw-token");
        when(tokenGenerator.hash("raw-token")).thenReturn("hash");

        service.resendVerification(email);

        verify(oneTimeTokenRepository).save(any(OneTimeToken.class));
        verify(eventPublisher).publishEmailVerificationRequested(eq(user), eq("raw-token"));
    }

    @Test
    void givenNonExistentEmail_whenResend_thenDoesNothingButDoesNotThrow() {
        when(userRepositoryPort.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.resendVerification("nobody@example.com"));

        verify(oneTimeTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEmailVerificationRequested(any(), any());
    }

    @Test
    void givenAlreadyVerifiedUser_whenResend_thenDoesNothing() {
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email("verified@example.com")
                .status("ACTIVE")
                .build();

        when(userRepositoryPort.findByEmail("verified@example.com")).thenReturn(Optional.of(user));

        service.resendVerification("verified@example.com");

        verify(oneTimeTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEmailVerificationRequested(any(), any());
    }

    @Test
    void givenActiveTokenAlreadyExists_whenResend_thenDoesNotGenerateNewToken() {
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email("unverified@example.com")
                .status("UNVERIFIED")
                .build();

        when(userRepositoryPort.findByEmail("unverified@example.com")).thenReturn(Optional.of(user));
        when(oneTimeTokenRepository.countActiveForUserAndType(user.getUserId(),
                OneTimeToken.TokenType.EMAIL_VERIFICATION)).thenReturn(1L);

        service.resendVerification("unverified@example.com");

        verify(oneTimeTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEmailVerificationRequested(any(), any());
    }
}
