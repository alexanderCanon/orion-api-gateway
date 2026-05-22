package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.LoginUserUseCase;
import com.orionticket.identity.application.port.out.JwtProviderPort;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginUserService implements LoginUserUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final JwtProviderPort jwtProviderPort;

    @Override
    public String login(String email, String rawPassword) {
        // 1. Buscar usuario por email
        User user = userRepositoryPort.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Correo o contraseña incorrectos."));

        // 2. Verificar contraseña
        if (!passwordHasherPort.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Correo o contraseña incorrectos.");
        }

        // 3. Generar JWT
        return jwtProviderPort.generateToken(user);
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepositoryPort.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Usuario no encontrado."));
    }
}
