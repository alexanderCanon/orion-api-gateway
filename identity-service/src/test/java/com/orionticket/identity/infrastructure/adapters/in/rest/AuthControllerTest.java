package com.orionticket.identity.infrastructure.adapters.in.rest;

import com.orionticket.identity.application.port.in.AuthResult;
import com.orionticket.identity.application.port.in.ChangePasswordUseCase;
import com.orionticket.identity.application.port.in.LoginUserUseCase;
import com.orionticket.identity.application.port.in.LogoutUseCase;
import com.orionticket.identity.application.port.in.RecoverPasswordUseCase;
import com.orionticket.identity.application.port.in.RefreshTokenUseCase;
import com.orionticket.identity.application.port.in.RegisterUserUseCase;
import com.orionticket.identity.application.port.in.ResendVerificationUseCase;
import com.orionticket.identity.application.port.in.ResetPasswordUseCase;
import com.orionticket.identity.application.port.in.VerifyEmailUseCase;
import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.LoginRequest;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.LoginResponse;
import com.orionticket.identity.infrastructure.adapters.out.security.AuthenticatedUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void loginReturnsAccessAndRefreshTokenAndUserContext() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        User user = User.builder()
                .userId(userId)
                .email("organizer@orionticket.com")
                .roleId(roleId)
                .organizerId(organizerId)
                .status("ACTIVE")
                .build();
        Role role = Role.builder()
                .roleId(roleId)
                .name("ORGANIZER")
                .permissions(List.of("events:create"))
                .build();
        AuthResult authResult = AuthResult.builder()
                .accessToken("signed.jwt")
                .refreshToken("opaque-refresh")
                .tokenType("Bearer")
                .expiresIn(900L)
                .user(user)
                .build();

        RegisterUserUseCase register = mock(RegisterUserUseCase.class);
        LoginUserUseCase login = mock(LoginUserUseCase.class);
        RefreshTokenUseCase refresh = mock(RefreshTokenUseCase.class);
        LogoutUseCase logout = mock(LogoutUseCase.class);
        RecoverPasswordUseCase recover = mock(RecoverPasswordUseCase.class);
        ResetPasswordUseCase reset = mock(ResetPasswordUseCase.class);
        VerifyEmailUseCase verify = mock(VerifyEmailUseCase.class);
        ResendVerificationUseCase resend = mock(ResendVerificationUseCase.class);
        ChangePasswordUseCase changePw = mock(ChangePasswordUseCase.class);
        RoleRepositoryPort roles = roleRepository(role);
        AuthenticatedUserResolver userResolver = mock(AuthenticatedUserResolver.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);

        when(httpRequest.getHeader("User-Agent")).thenReturn("test-ua");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(login.login("organizer@orionticket.com", "password123", "test-ua", "127.0.0.1"))
                .thenReturn(authResult);

        AuthController controller = new AuthController(register, login, refresh, logout,
                recover, reset, verify, resend, changePw, roles, userResolver);
        LoginRequest request = new LoginRequest();
        request.setEmail("organizer@orionticket.com");
        request.setPassword("password123");

        LoginResponse response = controller.login(request, httpRequest).getBody();

        assertEquals("signed.jwt", response.getAccessToken());
        assertEquals("opaque-refresh", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(900L, response.getExpiresIn());
        assertEquals(userId, response.getUserId());
        assertEquals("ORGANIZER", response.getRole());
        assertEquals(organizerId, response.getOrganizerId());
    }

    private static RoleRepositoryPort roleRepository(Role role) {
        return new RoleRepositoryPort() {
            @Override
            public Role save(Role ignored) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<Role> findById(UUID roleId) {
                return Optional.of(role);
            }

            @Override
            public List<Role> findAll() {
                return List.of(role);
            }

            @Override
            public void deleteById(UUID roleId) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
