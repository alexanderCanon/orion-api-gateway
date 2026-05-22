package com.orionticket.identity.application;

import com.orionticket.identity.application.port.out.JwtProviderPort;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.application.service.LoginUserService;
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

        User user = User.builder().email(email).passwordHash(hashedPassword).build();

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

        User user = User.builder().email(email).passwordHash(hashedPassword).build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches(rawPassword, hashedPassword)).thenReturn(false);

        // Act & Assert
        assertThrows(InvalidCredentialsException.class, () -> loginUserService.login(email, rawPassword));
    }
}
