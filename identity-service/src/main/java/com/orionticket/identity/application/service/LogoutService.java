package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.LogoutUseCase;
import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.domain.model.RefreshToken;
import com.orionticket.identity.domain.port.out.RefreshTokenRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {

    private final RefreshTokenRepositoryPort refreshTokenRepository;
    private final RefreshTokenGeneratorPort refreshTokenGenerator;
    private final AuditLogPort auditLogPort;

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        logout(rawRefreshToken, false);
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken, boolean all) {
        // Idempotente: logout de un token blank/null/desconocido no falla.
        // El controller ya valida con @NotBlank, pero por defensa en profundidad
        // el servicio también tolera entradas vacías sin lanzar.
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String hash = refreshTokenGenerator.hash(rawRefreshToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(hash);

        // Idempotente: logout de un token desconocido no falla, simplemente no hace nada.
        if (tokenOpt.isEmpty()) {
            return;
        }
        RefreshToken token = tokenOpt.get();

        if (all) {
            refreshTokenRepository.revokeAllForUser(token.getUserId());
            auditLogPort.logAction(token.getUserId(), "LOGOUT_ALL",
                    "All sessions revoked for user " + token.getUserId());
        } else {
            token.revoke();
            refreshTokenRepository.save(token);
            auditLogPort.logAction(token.getUserId(), "LOGOUT",
                    "Session revoked for user " + token.getUserId());
        }
    }
}
