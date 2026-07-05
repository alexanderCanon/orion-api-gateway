package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.AuthResult;
import com.orionticket.identity.application.port.in.LoginUserUseCase;
import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.JwtProviderPort;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.domain.exception.AccountDisabledException;
import com.orionticket.identity.domain.exception.AccountLockedException;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.model.RefreshToken;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RefreshTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
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
    private final AuditLogPort auditLogPort;

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

        // 2. Verificar si la cuenta está bloqueada por intentos fallidos (C4).
        //    Se hace antes de validar la contraseña para no gastar BCrypt
        //    innecesariamente y para informar al usuario legítimo cuándo reintentar.
        if (user.isLocked()) {
            long retryAfter = user.remainingLockSeconds();
            auditLogPort.logAction(user.getUserId(), "ACCOUNT_LOCKED_LOGIN_ATTEMPT",
                    "Locked account login attempt for " + email, ipAddress, userAgent);
            throw new AccountLockedException(retryAfter);
        }

        // 3. Verificar contraseña (mensaje idéntico al de "no existe")
        if (!passwordHasherPort.matches(rawPassword, user.getPasswordHash())) {
            boolean lockedNow = user.registerFailedLogin();
            userRepositoryPort.save(user);
            auditLogPort.logAction(user.getUserId(), "LOGIN_FAILED",
                    "Failed login attempt for " + email + " (attempt #" + user.getFailedLoginAttempts() + ")",
                    ipAddress, userAgent);
            if (lockedNow) {
                auditLogPort.logAction(user.getUserId(), "ACCOUNT_LOCKED",
                        "Account " + email + " locked after " + user.getFailedLoginAttempts() + " failed attempts",
                        ipAddress, userAgent);
            }
            throw new InvalidCredentialsException("Correo o contraseña incorrectos.");
        }

        // 4. Validar que la cuenta esté habilitada para autenticarse.
        //    Se hace DESPUÉS de validar la contraseña para no revelar el
        //    estado de la cuenta a un atacante que no conoce la contraseña.
        if (!user.canAuthenticate()) {
            throw new AccountDisabledException();
        }

        // 5. Login exitoso: resetear el contador de intentos fallidos.
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.resetFailedLoginAttempts();
            userRepositoryPort.save(user);
        }

        // 6. Generar access token JWT (corta vida)
        String accessToken = jwtProviderPort.generateToken(user);

        // 7. Generar refresh token opaco rotativo y persistirlo hasheado
        String rawRefreshToken = refreshTokenGenerator.generate();
        String refreshHash = refreshTokenGenerator.hash(rawRefreshToken);
        refreshTokenRepository.save(RefreshToken.builder()
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

        // 8. Auditar login exitoso (con IP y user-agent para investigación).
        auditLogPort.logAction(user.getUserId(), "LOGIN_SUCCESS",
                "Successful login for " + email, ipAddress, userAgent);

        return AuthResult.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType(AuthResult.TOKEN_TYPE)
                .expiresIn(accessExpirationSeconds)
                .user(user)
                .build();
    }
}
