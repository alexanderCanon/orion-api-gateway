package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.RegisterUserUseCase;
import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.IdentityEventPublisherPort;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.domain.exception.UserAlreadyExistsException;
import com.orionticket.identity.domain.model.OneTimeToken;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.OneTimeTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterUserService implements RegisterUserUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final RefreshTokenGeneratorPort tokenGenerator;
    private final OneTimeTokenRepositoryPort oneTimeTokenRepository;
    private final IdentityEventPublisherPort eventPublisher;
    private final AuditLogPort auditLogPort;

    // Asumiremos un UUID temporal para el rol de Comprador.
    // En produccion real esto se busca en la BD de roles.
    private static final UUID DEFAULT_BUYER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Value("${security.verification-token-ttl:86400}")
    private long verificationTokenTtlSeconds;

    @Override
    @Transactional
    public User registerBuyer(String email, String rawPassword, String fullName, String phone) {

        // 1. Validar que el email no exista
        if (userRepositoryPort.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("El correo " + email + " ya está registrado.");
        }

        // 2. Hashear el password (Nunca guardar texto plano)
        String hashedPassword = passwordHasherPort.hash(rawPassword);

        // 3. Crear el objeto de Dominio (La regla de UNVERIFIED se aplica adentro)
        User newUser = User.createBuyer(email, hashedPassword, fullName, phone, DEFAULT_BUYER_ROLE_ID);

        // 4. Persistir (con captura de race condition)
        User savedUser;
        try {
            savedUser = userRepositoryPort.save(newUser);
        } catch (DataIntegrityViolationException ex) {
            throw new UserAlreadyExistsException("El correo " + email + " ya está registrado.");
        }

        // 5. Auditar registro
        auditLogPort.logAction(savedUser.getUserId(), "USER_REGISTERED",
                "New buyer registered: " + email);

        // 6. Generar token de verificación de email y publicar evento
        try {
            String rawToken = tokenGenerator.generate();
            String tokenHash = tokenGenerator.hash(rawToken);
            oneTimeTokenRepository.save(OneTimeToken.builder()
                    .tokenId(UUID.randomUUID())
                    .userId(savedUser.getUserId())
                    .tokenHash(tokenHash)
                    .tokenType(OneTimeToken.TokenType.EMAIL_VERIFICATION)
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(verificationTokenTtlSeconds))
                    .usedAt(null)
                    .build());
            eventPublisher.publishEmailVerificationRequested(savedUser, rawToken);
        } catch (Exception ex) {
            // El registro fue exitoso pero el envío del email falló. No queremos
            // revertir el registro: el usuario puede solicitar reenvío.
            log.error("Failed to publish email verification event for {}: {}",
                    email, ex.getMessage(), ex);
        }

        return savedUser;
    }
}
