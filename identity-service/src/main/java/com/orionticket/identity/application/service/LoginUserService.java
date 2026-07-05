package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.AuthResult;
import com.orionticket.identity.application.port.in.LoginUserUseCase;
import com.orionticket.identity.application.port.out.JwtProviderPort;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
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
public class LoginUserService implements LoginUserUseCase {

    /**
     * Hash BCrypt pre-generado para una contraseña dummy.
     * Se usa cuando el usuario no existe, de modo que el coste de tiempo
     * del login sea equivalente al caso en que sí existe, mitigando el
     * oráculo de timing que permitiría enumerar emails registrados.
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final JwtProviderPort jwtProviderPort;
    private final RefreshTokenGeneratorPort refreshTokenGenerator;
    private final RefreshTokenRepositoryPort refreshTokenRepository;

    @Value("${jwt.expiration:${JWT_EXPIRATION:900}}")
    private long accessExpirationSeconds;

    @Value("${jwt.refresh-expiration:${JWT_REFRESH_EXPIRATION:2592000}}")
    private long refreshExpirationSeconds;

    @Override
    @Transactional
    public AuthResult login(String email, String rawPassword, String userAgent, String ipAddress) {
        Optional<User> userOpt = userRepositoryPort.findByEmail(email);

        // 1. Si el usuario no existe, ejecutamos BCrypt contra un hash dummy
        //    para mantener el tiempo de respuesta constante (anti timing-attack).
        if (userOpt.isEmpty()) {
            passwordHasherPort.matches(rawPassword, DUMMY_PASSWORD_HASH);
            throw new InvalidCredentialsException("Correo o contraseña incorrectos.");
        }
        User user = userOpt.get();

        // 2. Verificar contraseña (mensaje idéntico al de "no existe")
        if (!passwordHasherPort.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Correo o contraseña incorrectos.");
        }

        // 3. Validar que la cuenta esté habilitada para autenticarse.
        //    Se hace DESPUÉS de validar la contraseña para no revelar el
        //    estado de la cuenta a un atacante que no conoce la contraseña.
        if (!user.canAuthenticate()) {
            throw new AccountDisabledException();
        }

        // 4. Generar access token JWT (corta vida)
        String accessToken = jwtProviderPort.generateToken(user);

        // 5. Generar refresh token opaco rotativo y persistirlo hasheado
        String rawRefreshToken = refreshTokenGenerator.generate();
        String refreshHash = refreshTokenGenerator.hash(rawRefreshToken);
        RefreshToken persisted = refreshTokenRepository.save(RefreshToken.builder()
                .tokenId(UUID.randomUUID())
                .userId(user.getUserId())
                .tokenHash(refreshHash)
                .parentId(null)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(refreshExpirationSeconds))
                .revokedAt(null)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .build());

        return AuthResult.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType(AuthResult.TOKEN_TYPE)
                .expiresIn(accessExpirationSeconds)
                .user(user)
                .build();
    }
}
