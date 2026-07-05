package com.orionticket.identity.application;

import com.orionticket.identity.application.port.in.AuthResult;
import com.orionticket.identity.application.port.out.JwtProviderPort;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.application.service.LoginUserService;
import com.orionticket.identity.domain.exception.AccountDisabledException;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.model.RefreshToken;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RefreshTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock
    private RefreshTokenGeneratorPort refreshTokenGenerator;
    @Mock
    private RefreshTokenRepositoryPort refreshTokenRepository;

    private LoginUserService loginUserService;

    @BeforeEach
    void setUp() {
        loginUserService = new LoginUserService(userRepositoryPort, passwordHasherPort,
                jwtProviderPort, refreshTokenGenerator, refreshTokenRepository);
        ReflectionTestUtils.setField(loginUserService, "accessExpirationSeconds", 900L);
        ReflectionTestUtils.setField(loginUserService, "refreshExpirationSeconds", 2592000L);
    }

    @Test
    void givenValidCredentials_whenLogin_thenReturnAccessAndRefreshTokens() {
        String email = "test@example.com";
        String rawPassword = "password123";
        String hashedPassword = "hashedPassword";
        String expectedAccessToken = "jwt.token.here";
        String expectedRefreshToken = "opaque-refresh-token";

        User user = User.builder()
                .userId(java.util.UUID.randomUUID())
                .email(email)
                .passwordHash(hashedPassword)
                .status("ACTIVE")
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches(rawPassword, hashedPassword)).thenReturn(true);
        when(jwtProviderPort.generateToken(user)).thenReturn(expectedAccessToken);
        when(refreshTokenGenerator.generate()).thenReturn(expectedRefreshToken);
        when(refreshTokenGenerator.hash(expectedRefreshToken)).thenReturn("hash-value");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AuthResult result = loginUserService.login(email, rawPassword, "UA", "127.0.0.1");

        assertEquals(expectedAccessToken, result.accessToken());
        assertEquals(expectedRefreshToken, result.refreshToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(900L, result.expiresIn());
        assertEquals(user, result.user());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void givenInvalidPassword_whenLogin_thenThrowsException() {
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

        assertThrows(InvalidCredentialsException.class,
                () -> loginUserService.login(email, rawPassword, "UA", "127.0.0.1"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void givenSuspendedUserWithValidPassword_whenLogin_thenThrowsAccountDisabled() {
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

        assertThrows(AccountDisabledException.class,
                () -> loginUserService.login(email, rawPassword, "UA", "127.0.0.1"));
        verify(jwtProviderPort, never()).generateToken(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void givenUnverifiedUserWithValidPassword_whenLogin_thenReturnToken() {
        String email = "unverified@example.com";
        String rawPassword = "password123";
        String hashedPassword = "hashedPassword";

        User user = User.builder()
                .userId(java.util.UUID.randomUUID())
                .email(email)
                .passwordHash(hashedPassword)
                .status("UNVERIFIED")
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches(rawPassword, hashedPassword)).thenReturn(true);
        when(jwtProviderPort.generateToken(user)).thenReturn("jwt");
        when(refreshTokenGenerator.generate()).thenReturn("refresh");
        when(refreshTokenGenerator.hash("refresh")).thenReturn("hash");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AuthResult result = loginUserService.login(email, rawPassword, "UA", "127.0.0.1");

        assertEquals("jwt", result.accessToken());
        assertEquals("refresh", result.refreshToken());
    }

    @Test
    void givenNonExistentEmail_whenLogin_thenRunsDummyBcryptAndThrowsInvalidCredentials() {
        String email = "nobody@example.com";
        String rawPassword = "password123";

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordHasherPort.matches(eq(rawPassword), anyString())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> loginUserService.login(email, rawPassword, "UA", "127.0.0.1"));
        verify(passwordHasherPort).matches(eq(rawPassword), anyString());
        verify(jwtProviderPort, never()).generateToken(any());
        verify(refreshTokenRepository, never()).save(any());
    }
}
