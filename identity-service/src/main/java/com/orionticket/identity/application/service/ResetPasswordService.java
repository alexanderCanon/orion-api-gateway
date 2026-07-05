package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.ResetPasswordUseCase;
import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.model.OneTimeToken;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.OneTimeTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.RefreshTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementación de {@link ResetPasswordUseCase}.
 *
 * <p>Valida el token de recuperación, actualiza la contraseña, revoca
 * todos los refresh tokens del usuario e invalida otros tokens de
 * recovery pendientes. Mensaje genérico en caso de fallo (no revela
 * si el token existía, estaba expirado o ya fue usado).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResetPasswordService implements ResetPasswordUseCase {

    private static final String GENERIC_ERROR = "El token de recuperación es inválido o ha expirado.";

    private final OneTimeTokenRepositoryPort oneTimeTokenRepository;
    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final RefreshTokenRepositoryPort refreshTokenRepository;
    private final RefreshTokenGeneratorPort tokenGenerator;
    private final AuditLogPort auditLogPort;

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidCredentialsException(GENERIC_ERROR);
        }

        String tokenHash = tokenGenerator.hash(rawToken);
        Optional<OneTimeToken> tokenOpt = oneTimeTokenRepository.findByTokenHashAndType(
                tokenHash, OneTimeToken.TokenType.PASSWORD_RECOVERY);

        // Mensaje genérico para no revelar si el token existía, estaba expirado o ya fue usado.
        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid()) {
            throw new InvalidCredentialsException(GENERIC_ERROR);
        }
        OneTimeToken token = tokenOpt.get();

        User user = userRepositoryPort.findById(token.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException(GENERIC_ERROR));

        // Actualizar contraseña
        String newHash = passwordHasherPort.hash(newPassword);
        user.setPasswordHash(newHash);
        userRepositoryPort.save(user);

        // Marcar token como usado
        token.markUsed();
        oneTimeTokenRepository.save(token);

        // Invalidar otros tokens de recovery pendientes del usuario
        oneTimeTokenRepository.markAllUnusedForUserAndType(
                user.getUserId(), OneTimeToken.TokenType.PASSWORD_RECOVERY);

        // Revocar todas las sesiones activas (refresh tokens)
        refreshTokenRepository.revokeAllForUser(user.getUserId());

        auditLogPort.logAction(user.getUserId(), "PASSWORD_RECOVERED",
                "Password reset via recovery token for " + user.getEmail());
    }
}
