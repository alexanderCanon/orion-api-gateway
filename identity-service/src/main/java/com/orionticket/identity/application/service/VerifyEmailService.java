package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.VerifyEmailUseCase;
import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.model.OneTimeToken;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.OneTimeTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementación de {@link VerifyEmailUseCase}.
 *
 * <p>Valida el token de verificación y transiciona la cuenta de
 * {@code UNVERIFIED} a {@code ACTIVE} vía el método de dominio
 * {@link User#verifyEmail()}. Mensaje genérico en caso de fallo.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyEmailService implements VerifyEmailUseCase {

    private static final String GENERIC_ERROR = "El token de verificación es inválido o ha expirado.";

    private final OneTimeTokenRepositoryPort oneTimeTokenRepository;
    private final UserRepositoryPort userRepositoryPort;
    private final RefreshTokenGeneratorPort tokenGenerator;
    private final AuditLogPort auditLogPort;

    @Override
    @Transactional
    public void verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidCredentialsException(GENERIC_ERROR);
        }

        String tokenHash = tokenGenerator.hash(rawToken);
        Optional<OneTimeToken> tokenOpt = oneTimeTokenRepository.findByTokenHashAndType(
                tokenHash, OneTimeToken.TokenType.EMAIL_VERIFICATION);

        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid()) {
            throw new InvalidCredentialsException(GENERIC_ERROR);
        }
        OneTimeToken token = tokenOpt.get();

        User user = userRepositoryPort.findById(token.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException(GENERIC_ERROR));

        // Transición de dominio: UNVERIFIED → ACTIVE
        user.verifyEmail();
        userRepositoryPort.save(user);

        // Marcar token como usado
        token.markUsed();
        oneTimeTokenRepository.save(token);

        // Invalidar otros tokens de verificación pendientes
        oneTimeTokenRepository.markAllUnusedForUserAndType(
                user.getUserId(), OneTimeToken.TokenType.EMAIL_VERIFICATION);

        auditLogPort.logAction(user.getUserId(), "EMAIL_VERIFIED",
                "Email verified for " + user.getEmail());
    }
}
