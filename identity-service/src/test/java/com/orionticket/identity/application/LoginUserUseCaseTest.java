package com.orionticket.identity.application;

import com.orionticket.identity.application.port.out.JwtProviderPort;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.application.service.LoginUserService;
import com.orionticket.identity.domain.exception.AccountDisabledException;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUserUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    @Mock
    private PasswordHasherPort passwordHasherPort;
    @Mock
    private JwtProviderPort jwtProviderPort;

    private LoginUserService loginUserService;

    @BeforeEach
    void setUp() {
        loginUserService = new LoginUserService(userRepositoryPort, passwordHasherPort, jwtProviderPort);
    }

    @Test
    void givenValidCredentials_whenLogin_thenReturnJwtToken() {
        // Arrange
        String email = "test@example.com";
        String rawPassword = "password123";
        String hashedPassword = "hashedPassword";
        String expectedToken = "jwt.token.here";

        User user = User.builder()
                .email(email)
                .passwordHash(hashedPassword)
                .status("ACTIVE")
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches(rawPassword, hashedPassword)).thenReturn(true);
        when(jwtProviderPort.generateToken(user)).thenReturn(expectedToken);

        // Act
        String token = loginUserService.login(email, rawPassword);

        // Assert
        assertEquals(expectedToken, token);
    }

    @Test
    void givenInvalidPassword_whenLogin_thenThrowsException() {
        // Arrange
        String email = "test@example.com";
        String rawPassword = "wrongPassword";
        String hashedPassword = "hashedPassword";

        User user = User.builder()
                .email(email)
                .passwordHash(hashedPassword)
                .status("ACTIVE")
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches(rawPassword, hashedPassword)).thenReturn(false);

        // Act & Assert
        assertThrows(InvalidCredentialsException.class, () -> loginUserService.login(email, rawPassword));
    }

    @Test
    void givenSuspendedUserWithValidPassword_whenLogin_thenThrowsAccountDisabled() {
        // Arrange — un usuario SUSPENDED con contraseña correcta NO debe obtener token.
        String email = "suspended@example.com";
        String rawPassword = "password123";
        String hashedPassword = "hashedPassword";

        User user = User.builder()
                .email(email)
                .passwordHash(hashedPassword)
                .status("SUSPENDED")
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches(rawPassword, hashedPassword)).thenReturn(true);

        // Act & Assert
        assertThrows(AccountDisabledException.class, () -> loginUserService.login(email, rawPassword));
        // Y por seguridad: nunca se genera el token.
        verify(jwtProviderPort, never()).generateToken(any());
    }

    @Test
    void givenUnverifiedUserWithValidPassword_whenLogin_thenReturnToken() {
        // Arrange — UNVERIFIED puede autenticarse (política actual, previa a Fase 3).
        String email = "unverified@example.com";
        String rawPassword = "password123";
        String hashedPassword = "hashedPassword";
        String expectedToken = "jwt.token.here";

        User user = User.builder()
                .email(email)
                .passwordHash(hashedPassword)
                .status("UNVERIFIED")
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches(rawPassword, hashedPassword)).thenReturn(true);
        when(jwtProviderPort.generateToken(user)).thenReturn(expectedToken);

        // Act
        String token = loginUserService.login(email, rawPassword);

        // Assert
        assertEquals(expectedToken, token);
    }

    @Test
    void givenNonExistentEmail_whenLogin_thenRunsDummyBcryptAndThrowsInvalidCredentials() {
        // Arrange — anti timing-attack: se ejecuta BCrypt contra un hash dummy
        // aunque el usuario no exista, para mantener el tiempo de respuesta constante.
        String email = "nobody@example.com";
        String rawPassword = "password123";

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordHasherPort.matches(eq(rawPassword), anyString())).thenReturn(false);

        // Act & Assert
        assertThrows(InvalidCredentialsException.class, () -> loginUserService.login(email, rawPassword));
        // Verifica que se haya ejecutado BCrypt (anti timing) aunque el usuario no exista.
        verify(passwordHasherPort).matches(eq(rawPassword), anyString());
        verify(jwtProviderPort, never()).generateToken(any());
    }
}
