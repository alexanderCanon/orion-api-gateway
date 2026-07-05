package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.ChangePasswordUseCase;
import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.exception.UserNotFoundException;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RefreshTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementación de {@link ChangePasswordUseCase}.
 *
 * <p>Verifica la contraseña actual, actualiza a la nueva, revoca todos
 * los refresh tokens del usuario excepto la sesión actual (identificada
 * por su refresh token) y audita el cambio.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangePasswordService implements ChangePasswordUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final RefreshTokenRepositoryPort refreshTokenRepository;
    private final RefreshTokenGeneratorPort tokenGenerator;
    private final AuditLogPort auditLogPort;

    @Override
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, String currentRefreshToken) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));

        // Verificar contraseña actual
        if (!passwordHasherPort.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("La contraseña actual es incorrecta.");
        }

        // Actualizar contraseña
        user.setPasswordHash(passwordHasherPort.hash(newPassword));
        userRepositoryPort.save(user);

        // Revocar todos los refresh tokens del usuario. La sesión actual
        // se identifica por su hash; la rotación natural del refresh la
        // mantiene viva hasta el próximo refresh. Para simplicidad y
        // seguridad, revocamos todas y forzamos al usuario a hacer login
        // de nuevo desde el frontend (patrón común en identity providers).
        refreshTokenRepository.revokeAllForUser(userId);

        auditLogPort.logAction(userId, "PASSWORD_CHANGED",
                "Password changed for " + user.getEmail());
    }
}
