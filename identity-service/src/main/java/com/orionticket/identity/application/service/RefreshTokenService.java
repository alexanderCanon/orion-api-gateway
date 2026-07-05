package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.AuthResult;
import com.orionticket.identity.application.port.in.RefreshTokenUseCase;
import com.orionticket.identity.application.port.out.JwtProviderPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.domain.exception.AccountDisabledException;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.model.RefreshToken;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RefreshTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService implements RefreshTokenUseCase {

    private final RefreshTokenRepositoryPort refreshTokenRepository;
    private final UserRepositoryPort userRepositoryPort;
    private final JwtProviderPort jwtProviderPort;
    private final RefreshTokenGeneratorPort refreshTokenGenerator;

    @Value("${jwt.expiration:${JWT_EXPIRATION:900}}")
    private long accessExpirationSeconds;

    @Value("${jwt.refresh-expiration:${JWT_REFRESH_EXPIRATION:2592000}}")
    private long refreshExpirationSeconds;

    @Override
    @Transactional
    public AuthResult refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidCredentialsException("Refresh token is required.");
        }

        String hash = refreshTokenGenerator.hash(rawRefreshToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(hash);

        // Token no encontrado: no revelar existencia, pero no hay nada que revocar.
        if (tokenOpt.isEmpty()) {
            throw new InvalidCredentialsException("Invalid or expired refresh token.");
        }
        RefreshToken token = tokenOpt.get();

        // DETECCIÓN DE REUSO: si el token ya está revocado, significa que ya se
        // rotó y alguien está presentando una copia (posible robo). Se revoca
        // toda la cadena del usuario para invalidar al atacante.
        if (token.isRevoked()) {
            refreshTokenRepository.revokeChain(token.getTokenId());
            throw new InvalidCredentialsException("Invalid or expired refresh token.");
        }

        // Expirado: se revoca y se rechaza.
        if (token.isExpired()) {
            token.revoke();
            refreshTokenRepository.save(token);
            throw new InvalidCredentialsException("Invalid or expired refresh token.");
        }

        // Cargar usuario y verificar que siga habilitado. Un usuario suspendido
        // no puede refrescar: así la suspensión surte efecto en ≤ TTL del access.
        User user = userRepositoryPort.findById(token.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired refresh token."));
        if (!user.canAuthenticate()) {
            // Revocar todos los tokens del usuario suspendido.
            refreshTokenRepository.revokeAllForUser(user.getUserId());
            throw new AccountDisabledException();
        }

        // ROTACIÓN: revocar el token actual y emitir uno nuevo encadenado.
        token.revoke();
        refreshTokenRepository.save(token);

        String newAccessToken = jwtProviderPort.generateToken(user);
        String newRawRefresh = refreshTokenGenerator.generate();
        String newHash = refreshTokenGenerator.hash(newRawRefresh);
        refreshTokenRepository.save(RefreshToken.builder()
                .tokenId(UUID.randomUUID())
                .userId(user.getUserId())
                .tokenHash(newHash)
                .parentId(token.getTokenId())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(refreshExpirationSeconds))
                .revokedAt(null)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .build());

        return AuthResult.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefresh)
                .tokenType(AuthResult.TOKEN_TYPE)
                .expiresIn(accessExpirationSeconds)
                .user(user)
                .build();
    }
}
