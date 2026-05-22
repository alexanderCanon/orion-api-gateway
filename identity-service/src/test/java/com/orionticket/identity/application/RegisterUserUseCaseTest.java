package com.orionticket.identity.application;

import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.application.service.RegisterUserService;
import com.orionticket.identity.domain.exception.UserAlreadyExistsException;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private PasswordHasherPort passwordHasherPort;

    private RegisterUserService registerUserService;

    @BeforeEach
    void setUp() {
        registerUserService = new RegisterUserService(userRepositoryPort, passwordHasherPort);
    }

    @Test
    void givenValidData_whenRegisterUser_thenUserIsUnverifiedAndPasswordIsHashed() {
        // Arrange
        String email = "test@example.com";
        String rawPassword = "password123";
        String fullName = "Test User";
        String phone = "12345678";

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordHasherPort.hash(rawPassword)).thenReturn("hashed_password_abc");
        when(userRepositoryPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User createdUser = registerUserService.registerBuyer(email, rawPassword, fullName, phone);

        // Assert
        assertNotNull(createdUser.getUserId());
        assertEquals(email, createdUser.getEmail());
        assertEquals("UNVERIFIED", createdUser.getStatus(), "El usuario debe iniciar como UNVERIFIED");
        assertEquals("hashed_password_abc", createdUser.getPasswordHash(), "La contraseña debe guardarse con hash");
        
        verify(userRepositoryPort, times(1)).save(any(User.class));
    }

    @Test
    void givenExistingEmail_whenRegisterUser_thenThrowsException() {
        // Arrange
        String email = "existente@example.com";
        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(User.builder().build()));

        // Act & Assert
        assertThrows(UserAlreadyExistsException.class, () -> 
            registerUserService.registerBuyer(email, "pass", "Name", "123")
        );
        
        verify(userRepositoryPort, never()).save(any(User.class));
    }
}
