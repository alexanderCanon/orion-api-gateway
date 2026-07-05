package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.LoginUserUseCase;
import com.orionticket.identity.application.port.out.JwtProviderPort;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.domain.exception.AccountDisabledException;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

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

    @Override
    public String login(String email, String rawPassword) {
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

        // 4. Generar JWT
        return jwtProviderPort.generateToken(user);
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepositoryPort.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Usuario no encontrado."));
    }
}
