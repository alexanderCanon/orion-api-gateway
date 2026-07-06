package com.orionticket.identity.application;

import com.orionticket.identity.application.port.in.AuthResult;
import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.JwtProviderPort;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.application.service.LoginUserService;
import com.orionticket.identity.domain.exception.AccountDisabledException;
import com.orionticket.identity.domain.exception.AccountLockedException;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.model.RefreshToken;
import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RefreshTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

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
    @Mock
    private AuditLogPort auditLogPort;
    @Mock
    private RoleRepositoryPort roleRepositoryPort;

    private LoginUserService loginUserService;

    @BeforeEach
    void setUp() {
        loginUserService = new LoginUserService(userRepositoryPort, passwordHasherPort,
                jwtProviderPort, refreshTokenGenerator, refreshTokenRepository, auditLogPort,
                roleRepositoryPort);
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
                .failedLoginAttempts(0)
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches(rawPassword, hashedPassword)).thenReturn(true);
        when(jwtProviderPort.generateToken(user)).thenReturn(expectedAccessToken);
        when(refreshTokenGenerator.generate()).thenReturn(expectedRefreshToken);
        when(refreshTokenGenerator.hash(expectedRefreshToken)).thenReturn("hash-value");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(roleRepositoryPort.findById(nullable(UUID.class))).thenReturn(Optional.of(
                Role.builder().roleId(UUID.randomUUID()).name("BUYER").build()));

        AuthResult result = loginUserService.login(email, rawPassword, "UA", "127.0.0.1");

        assertEquals(expectedAccessToken, result.accessToken());
        assertEquals(expectedRefreshToken, result.refreshToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(900L, result.expiresIn());
        assertEquals(user, result.user());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        // Fase 4: login exitoso debe auditar LOGIN_SUCCESS con IP + UA.
        verify(auditLogPort).logAction(eq(user.getUserId()), eq("LOGIN_SUCCESS"),
                anyString(), eq("127.0.0.1"), eq("UA"));
    }

    @Test
    void givenInvalidPassword_whenLogin_thenThrowsExceptionAndIncrementsCounter() {
        String email = "test@example.com";
        String rawPassword = "wrongPassword";
        String hashedPassword = "hashedPassword";

        User user = User.builder()
                .userId(java.util.UUID.randomUUID())
                .email(email)
                .passwordHash(hashedPassword)
                .status("ACTIVE")
                .failedLoginAttempts(0)
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches(rawPassword, hashedPassword)).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> loginUserService.login(email, rawPassword, "UA", "127.0.0.1"));

        assertEquals(1, user.getFailedLoginAttempts());
        verify(userRepositoryPort).save(user);
        verify(auditLogPort).logAction(eq(user.getUserId()), eq("LOGIN_FAILED"), anyString(), eq("127.0.0.1"), eq("UA"));
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
                .failedLoginAttempts(0)
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
                .failedLoginAttempts(0)
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches(rawPassword, hashedPassword)).thenReturn(true);
        when(jwtProviderPort.generateToken(user)).thenReturn("jwt");
        when(refreshTokenGenerator.generate()).thenReturn("refresh");
        when(refreshTokenGenerator.hash("refresh")).thenReturn("hash");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(roleRepositoryPort.findById(nullable(UUID.class))).thenReturn(Optional.of(
                Role.builder().roleId(UUID.randomUUID()).name("BUYER").build()));

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

    // --- Tests de lockout por fuerza bruta (Fase 2, C4) ---

    @Test
    void givenLockedAccount_whenLogin_thenThrowsAccountLockedWithRetryAfter() {
        String email = "locked@example.com";
        String rawPassword = "password123";
        String hashedPassword = "hashedPassword";

        User user = User.builder()
                .userId(java.util.UUID.randomUUID())
                .email(email)
                .passwordHash(hashedPassword)
                .status("ACTIVE")
                .failedLoginAttempts(5)
                .lockedUntil(Instant.now().plusSeconds(600))
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));

        AccountLockedException ex = assertThrows(AccountLockedException.class,
                () -> loginUserService.login(email, rawPassword, "UA", "127.0.0.1"));

        assertTrue(ex.getRetryAfterSeconds() > 0);
        // No debe validar la contraseña ni generar tokens si la cuenta está bloqueada.
        verify(passwordHasherPort, never()).matches(anyString(), anyString());
        verify(jwtProviderPort, never()).generateToken(any());
        verify(refreshTokenRepository, never()).save(any());
        verify(auditLogPort).logAction(eq(user.getUserId()), eq("ACCOUNT_LOCKED_LOGIN_ATTEMPT"), anyString(), eq("127.0.0.1"), eq("UA"));
    }

    @Test
    void givenFiveFailedAttempts_whenFifthFailure_thenAccountGetsLocked() {
        String email = "bruteforce@example.com";
        String rawPassword = "wrongPassword";
        String hashedPassword = "hashedPassword";

        // El usuario ya tiene 4 intentos fallidos; el 5.º dispara el lockout.
        User user = User.builder()
                .userId(java.util.UUID.randomUUID())
                .email(email)
                .passwordHash(hashedPassword)
                .status("ACTIVE")
                .failedLoginAttempts(4)
                .lockedUntil(null)
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches(rawPassword, hashedPassword)).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> loginUserService.login(email, rawPassword, "UA", "127.0.0.1"));

        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil());
        assertTrue(user.isLocked());
        verify(userRepositoryPort).save(user);
        verify(auditLogPort).logAction(eq(user.getUserId()), eq("LOGIN_FAILED"), anyString(), eq("127.0.0.1"), eq("UA"));
        verify(auditLogPort).logAction(eq(user.getUserId()), eq("ACCOUNT_LOCKED"), anyString(), eq("127.0.0.1"), eq("UA"));
    }

    @Test
    void givenSuccessfulLoginAfterFailedAttempts_thenCounterIsReset() {
        String email = "recovered@example.com";
        String rawPassword = "correctPassword";
        String hashedPassword = "hashedPassword";

        User user = User.builder()
                .userId(java.util.UUID.randomUUID())
                .email(email)
                .passwordHash(hashedPassword)
                .status("ACTIVE")
                .failedLoginAttempts(3)
                .lockedUntil(null)
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches(rawPassword, hashedPassword)).thenReturn(true);
        when(jwtProviderPort.generateToken(user)).thenReturn("jwt");
        when(refreshTokenGenerator.generate()).thenReturn("refresh");
        when(refreshTokenGenerator.hash("refresh")).thenReturn("hash");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(roleRepositoryPort.findById(nullable(UUID.class))).thenReturn(Optional.of(
                Role.builder().roleId(UUID.randomUUID()).name("BUYER").build()));

        AuthResult result = loginUserService.login(email, rawPassword, "UA", "127.0.0.1");

        assertEquals("jwt", result.accessToken());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        verify(userRepositoryPort).save(user);
    }

    @Test
    void givenExpiredLock_whenLoginWithCorrectPassword_thenSucceedsAndResets() {
        String email = "expiredlock@example.com";
        String rawPassword = "correctPassword";
        String hashedPassword = "hashedPassword";

        // El lockout ya expiró (lockedUntil en el pasado).
        User user = User.builder()
                .userId(java.util.UUID.randomUUID())
                .email(email)
                .passwordHash(hashedPassword)
                .status("ACTIVE")
                .failedLoginAttempts(5)
                .lockedUntil(Instant.now().minusSeconds(60))
                .build();

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches(rawPassword, hashedPassword)).thenReturn(true);
        when(jwtProviderPort.generateToken(user)).thenReturn("jwt");
        when(refreshTokenGenerator.generate()).thenReturn("refresh");
        when(refreshTokenGenerator.hash("refresh")).thenReturn("hash");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(roleRepositoryPort.findById(nullable(UUID.class))).thenReturn(Optional.of(
                Role.builder().roleId(UUID.randomUUID()).name("BUYER").build()));

        AuthResult result = loginUserService.login(email, rawPassword, "UA", "127.0.0.1");

        assertEquals("jwt", result.accessToken());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }
}
