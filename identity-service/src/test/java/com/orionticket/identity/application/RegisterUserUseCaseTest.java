package com.orionticket.identity.application;

import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.IdentityEventPublisherPort;
import com.orionticket.identity.application.port.out.PasswordHasherPort;
import com.orionticket.identity.application.port.out.RefreshTokenGeneratorPort;
import com.orionticket.identity.application.service.RegisterUserService;
import com.orionticket.identity.domain.exception.UserAlreadyExistsException;
import com.orionticket.identity.domain.model.OneTimeToken;
import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.OneTimeTokenRepositoryPort;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    @Mock
    private PasswordHasherPort passwordHasherPort;
    @Mock
    private RefreshTokenGeneratorPort tokenGenerator;
    @Mock
    private OneTimeTokenRepositoryPort oneTimeTokenRepository;
    @Mock
    private IdentityEventPublisherPort eventPublisher;
    @Mock
    private AuditLogPort auditLogPort;
    @Mock
    private RoleRepositoryPort roleRepositoryPort;

    private RegisterUserService registerUserService;

    @BeforeEach
    void setUp() {
        registerUserService = new RegisterUserService(userRepositoryPort, passwordHasherPort,
                tokenGenerator, oneTimeTokenRepository, eventPublisher, auditLogPort,
                roleRepositoryPort);
        ReflectionTestUtils.setField(registerUserService, "verificationTokenTtlSeconds", 86400L);
    }

    @Test
    void givenValidData_whenRegisterUser_thenUserIsUnverifiedAndPasswordIsHashed() {
        String email = "test@example.com";
        String rawPassword = "password123";
        String fullName = "Test User";
        String phone = "12345678";

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordHasherPort.hash(rawPassword)).thenReturn("hashed_password_abc");
        when(userRepositoryPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenGenerator.generate()).thenReturn("raw-token");
        when(tokenGenerator.hash("raw-token")).thenReturn("hash");
        when(roleRepositoryPort.findByName("BUYER")).thenReturn(Optional.of(
                Role.builder().roleId(UUID.randomUUID()).name("BUYER").build()));

        User createdUser = registerUserService.registerBuyer(email, rawPassword, fullName, phone);

        assertNotNull(createdUser.getUserId());
        assertEquals(email, createdUser.getEmail());
        assertEquals("UNVERIFIED", createdUser.getStatus(), "El usuario debe iniciar como UNVERIFIED");
        assertEquals("hashed_password_abc", createdUser.getPasswordHash(), "La contraseña debe guardarse con hash");

        verify(userRepositoryPort, times(1)).save(any(User.class));
        verify(oneTimeTokenRepository).save(any(OneTimeToken.class));
        verify(eventPublisher).publishEmailVerificationRequested(any(User.class), eq("raw-token"));
        // Fase 4: registro debe auditar USER_REGISTERED.
        verify(auditLogPort).logAction(eq(createdUser.getUserId()), eq("USER_REGISTERED"), anyString());
    }

    @Test
    void givenExistingEmail_whenRegisterUser_thenThrowsException() {
        String email = "existente@example.com";
        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(User.builder().build()));

        assertThrows(UserAlreadyExistsException.class, () ->
            registerUserService.registerBuyer(email, "pass", "Name", "123")
        );

        verify(userRepositoryPort, never()).save(any(User.class));
        verify(eventPublisher, never()).publishEmailVerificationRequested(any(), any());
    }

    @Test
    void givenValidData_whenRegisterUser_thenResolvesBuyerRoleByNameNotByUuid() {
        // Fase 5.1: el rol BUYER debe resolverse por nombre (findByName),
        // no por un UUID mágico hardcodeado.
        UUID buyerRoleId = UUID.randomUUID();
        when(userRepositoryPort.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordHasherPort.hash(any())).thenReturn("hash");
        when(userRepositoryPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenGenerator.generate()).thenReturn("tok");
        when(tokenGenerator.hash("tok")).thenReturn("tok-hash");
        when(roleRepositoryPort.findByName("BUYER")).thenReturn(Optional.of(
                Role.builder().roleId(buyerRoleId).name("BUYER").build()));

        User createdUser = registerUserService.registerBuyer("x@y.com", "pw", "Name", "123");

        // Verificar que se llamó findByName con el nombre canónico del enum.
        verify(roleRepositoryPort).findByName("BUYER");
        // El roleId del usuario debe ser el que devolvió findByName, no un UUID mágico.
        assertEquals(buyerRoleId, createdUser.getRoleId());
        // No se debe haber usado findById en el flujo de registro.
        verify(roleRepositoryPort, never()).findById(any());
    }

    @Test
    void givenBuyerRoleNotFound_whenRegisterUser_thenThrowsIllegalState() {
        // Fase 5.1: si el rol BUYER no existe en la BD, el servicio debe
        // fallar rápido con IllegalStateException (error de configuración).
        when(userRepositoryPort.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordHasherPort.hash(any())).thenReturn("hash");
        when(roleRepositoryPort.findByName("BUYER")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () ->
            registerUserService.registerBuyer("x@y.com", "pw", "Name", "123")
        );

        verify(userRepositoryPort, never()).save(any(User.class));
    }
}
