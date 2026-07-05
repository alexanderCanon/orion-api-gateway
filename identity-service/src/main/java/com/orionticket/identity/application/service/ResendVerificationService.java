package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.ResendVerificationUseCase;
import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.IdentityEventPublisherPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.domain.model.OneTimeToken;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.OneTimeTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación de {@link ResendVerificationUseCase}.
 *
 * <p>Anti-enumeración: responde siempre 200 OK. Solo reenvía si el
 * usuario existe y está en estado {@code UNVERIFIED}. Rate limiting:
 * 1 reenvío cada 60s (se checa via {@code countActiveForUserAndType}).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResendVerificationService implements ResendVerificationUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final OneTimeTokenRepositoryPort oneTimeTokenRepository;
    private final RefreshTokenGeneratorPort tokenGenerator;
    private final IdentityEventPublisherPort eventPublisher;
    private final AuditLogPort auditLogPort;

    @Value("${security.verification-token-ttl:86400}")
    private long verificationTokenTtlSeconds;

    @Override
    @Transactional
    public void resendVerification(String email) {
        Optional<User> userOpt = userRepositoryPort.findByEmail(email);

        // Anti-enumeración: no revelar si el email existe o no.
        if (userOpt.isEmpty()) {
            log.debug("Resend verification requested for non-existent email: {}", email);
            return;
        }
        User user = userOpt.get();

        // Solo reenviar si el usuario aún no ha verificado su email.
        if (user.isActive()) {
            log.debug("Resend verification requested for already-verified email: {}", email);
            return;
        }

        // Rate limiting: 1 token activo a la vez (evita spam).
        long activeTokens = oneTimeTokenRepository.countActiveForUserAndType(
                user.getUserId(), OneTimeToken.TokenType.EMAIL_VERIFICATION);
        if (activeTokens > 0) {
            log.debug("Verification already requested recently for: {}", email);
            return;
        }

        // Generar nuevo token y publicar evento.
        String rawToken = tokenGenerator.generate();
        String tokenHash = tokenGenerator.hash(rawToken);
        oneTimeTokenRepository.save(OneTimeToken.builder()
                .tokenId(UUID.randomUUID())
                .userId(user.getUserId())
                .tokenHash(tokenHash)
                .tokenType(OneTimeToken.TokenType.EMAIL_VERIFICATION)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(verificationTokenTtlSeconds))
                .usedAt(null)
                .build());

        eventPublisher.publishEmailVerificationRequested(user, rawToken);
        auditLogPort.logAction(user.getUserId(), "VERIFICATION_RESENT",
                "Verification email resent for " + email);
    }
}
