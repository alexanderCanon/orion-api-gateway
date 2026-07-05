package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.RecoverPasswordUseCase;
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
 * Implementación de {@link RecoverPasswordUseCase}.
 *
 * <p>Anti-enumeración: responde siempre 200 OK exista o no el email.
 * Si el email existe, genera un token opaco de un solo uso (TTL 1h),
 * lo persiste hasheado y publica un evento RabbitMQ para que el
 * notification-service envíe el email. Rate limiting: 1 token cada 60s
 * por usuario (se checa via {@code countActiveForUserAndType}).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecoverPasswordService implements RecoverPasswordUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final OneTimeTokenRepositoryPort oneTimeTokenRepository;
    private final RefreshTokenGeneratorPort tokenGenerator;
    private final IdentityEventPublisherPort eventPublisher;
    private final AuditLogPort auditLogPort;

    @Value("${security.recovery-token-ttl:3600}")
    private long recoveryTokenTtlSeconds;

    @Value("${security.recovery-token-min-interval:60}")
    private long recoveryTokenMinIntervalSeconds;

    @Override
    @Transactional
    public void requestPasswordRecovery(String email) {
        Optional<User> userOpt = userRepositoryPort.findByEmail(email);

        // Anti-enumeración: no revelar si el email existe o no.
        // Si no existe, simplemente no hacemos nada y devolvemos 200.
        if (userOpt.isEmpty()) {
            log.debug("Password recovery requested for non-existent email: {}", email);
            return;
        }
        User user = userOpt.get();

        // Rate limiting: 1 token cada 60s por usuario.
        // Si ya hay un token activo, no generar otro (evita spam y abuso).
        long activeTokens = oneTimeTokenRepository.countActiveForUserAndType(
                user.getUserId(), OneTimeToken.TokenType.PASSWORD_RECOVERY);
        if (activeTokens > 0) {
            log.debug("Password recovery already requested recently for: {}", email);
            return;
        }

        // Generar token opaco, persistir hasheado y publicar evento.
        String rawToken = tokenGenerator.generate();
        String tokenHash = tokenGenerator.hash(rawToken);
        oneTimeTokenRepository.save(OneTimeToken.builder()
                .tokenId(UUID.randomUUID())
                .userId(user.getUserId())
                .tokenHash(tokenHash)
                .tokenType(OneTimeToken.TokenType.PASSWORD_RECOVERY)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(recoveryTokenTtlSeconds))
                .usedAt(null)
                .build());

        eventPublisher.publishPasswordRecoveryRequested(user, rawToken);
        auditLogPort.logAction(user.getUserId(), "PASSWORD_RECOVERY_REQUESTED",
                "Password recovery requested for " + email);
    }
}
