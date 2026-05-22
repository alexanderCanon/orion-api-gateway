package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.RegisterUserUseCase;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.domain.exception.UserAlreadyExistsException;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegisterUserService implements RegisterUserUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    
    // Asumiremos un UUID temporal para el rol de Comprador. 
    // En produccion real esto se busca en la BD de roles.
    private static final UUID DEFAULT_BUYER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public User registerBuyer(String email, String rawPassword, String fullName, String phone) {
        
        // 1. Validar que el email no exista
        if (userRepositoryPort.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("El correo " + email + " ya está registrado.");
        }

        // 2. Hashear el password (Nunca guardar texto plano)
        String hashedPassword = passwordHasherPort.hash(rawPassword);

        // 3. Crear el objeto de Dominio (La regla de UNVERIFIED se aplica adentro)
        User newUser = User.createBuyer(email, hashedPassword, fullName, phone, DEFAULT_BUYER_ROLE_ID);

        // 4. Persistir
        return userRepositoryPort.save(newUser);
    }
}
