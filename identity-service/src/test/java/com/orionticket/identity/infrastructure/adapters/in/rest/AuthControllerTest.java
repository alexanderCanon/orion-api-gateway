package com.orionticket.identity.infrastructure.adapters.in.rest;

import com.orionticket.identity.application.port.in.LoginUserUseCase;
import com.orionticket.identity.application.port.in.RegisterUserUseCase;
import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.LoginRequest;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.LoginResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void loginReturnsBearerTokenAndAuthenticatedUserContext() {
        RegisterUserUseCase register = mock(RegisterUserUseCase.class);
        LoginUserUseCase login = mock(LoginUserUseCase.class);
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        User user = User.builder()
                .userId(userId)
                .email("organizer@orionticket.com")
                .roleId(roleId)
                .organizerId(organizerId)
                .build();
        RoleRepositoryPort roles = roleRepository(Role.builder()
                .roleId(roleId)
                .name("ORGANIZER")
                .permissions(List.of("events:create"))
                .build());
        AuthController controller = new AuthController(register, login, roles, 3600);
        LoginRequest request = new LoginRequest();
        request.setEmail("organizer@orionticket.com");
        request.setPassword("password123");

        when(login.login("organizer@orionticket.com", "password123")).thenReturn("signed.jwt");
        when(login.getUserByEmail("organizer@orionticket.com")).thenReturn(user);

        LoginResponse response = controller.login(request).getBody();

        assertEquals("signed.jwt", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600, response.getExpiresIn());
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
