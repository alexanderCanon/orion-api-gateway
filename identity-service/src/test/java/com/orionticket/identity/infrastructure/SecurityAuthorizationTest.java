package com.orionticket.identity.infrastructure;

import com.orionticket.identity.application.port.in.LoginUserUseCase;
import com.orionticket.identity.application.port.in.RegisterUserUseCase;
import com.orionticket.identity.application.port.in.RoleManagementUseCase;
import com.orionticket.identity.application.port.in.UserManagementUseCase;
import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import com.orionticket.identity.infrastructure.adapters.in.rest.AuthController;
import com.orionticket.identity.infrastructure.adapters.in.rest.OrganizerStaffController;
import com.orionticket.identity.infrastructure.adapters.in.rest.RoleManagementController;
import com.orionticket.identity.infrastructure.adapters.in.rest.UserManagementController;
import com.orionticket.identity.infrastructure.adapters.out.security.AuthenticatedUser;
import com.orionticket.identity.infrastructure.adapters.out.security.AuthenticatedUserResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthController.class,
        UserManagementController.class,
        RoleManagementController.class,
        OrganizerStaffController.class
})
@Import(SecurityTestConfig.class)
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegisterUserUseCase registerUserUseCase;

    @MockBean
    private LoginUserUseCase loginUserUseCase;

    @MockBean
    private UserManagementUseCase userManagementUseCase;

    @MockBean
    private RoleManagementUseCase roleManagementUseCase;

    @MockBean
    private RoleRepositoryPort roleRepositoryPort;

    @MockBean
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Test
    void usersEndpointWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersEndpointWithNonSuperAdminRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/v1/users")
                        .with(jwt().jwt(jwt -> jwt.claim("role", "ORGANIZER"))
                                .authorities(() -> "ROLE_ORGANIZER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolesEndpointWithSuperAdminRoleIsAllowed() throws Exception {
        when(roleManagementUseCase.getAllRoles()).thenReturn(List.of());

        mockMvc.perform(get("/v1/roles")
                        .with(jwt().jwt(jwt -> jwt.claim("role", "SUPER_ADMIN"))
                                .authorities(() -> "ROLE_SUPER_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void organizerCanCreateStaffForOwnedOrganizer() throws Exception {
        UUID organizerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID roleId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        User staff = User.builder()
                .userId(UUID.randomUUID())
                .email("door@orionticket.com")
                .fullName("Door Validator")
                .status("ACTIVE")
                .roleId(roleId)
                .organizerId(organizerId)
                .build();
        when(authenticatedUserResolver.requireOrganizerOwnership(organizerId))
                .thenReturn(new AuthenticatedUser(creatorId, "ORGANIZER", organizerId));
        when(userManagementUseCase.createOrganizerStaff(eq(organizerId), eq("door@orionticket.com"),
                any(), eq("Door Validator"), eq("555-0101"), eq(roleId), eq(creatorId)))
                .thenReturn(staff);

        mockMvc.perform(post("/v1/organizers/" + organizerId + "/staff")
                        .with(jwt().jwt(jwt -> jwt.claim("role", "ORGANIZER"))
                                .authorities(() -> "ROLE_ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "door@orionticket.com",
                                  "password": "password123",
                                  "fullName": "Door Validator",
                                  "phone": "555-0101",
                                  "roleId": "%s"
                                }
                                """.formatted(roleId)))
                .andExpect(status().isCreated());
    }

    @Test
    void organizerCannotCreateStaffForAnotherOrganizer() throws Exception {
        UUID organizerId = UUID.randomUUID();
        UUID roleId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        when(authenticatedUserResolver.requireOrganizerOwnership(organizerId))
                .thenThrow(new AccessDeniedException("Caller cannot manage this organizer"));

        mockMvc.perform(post("/v1/organizers/" + organizerId + "/staff")
                        .with(jwt().jwt(jwt -> jwt.claim("role", "ORGANIZER"))
                                .authorities(() -> "ROLE_ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "door@orionticket.com",
                                  "password": "password123",
                                  "fullName": "Door Validator",
                                  "phone": "555-0101",
                                  "roleId": "%s"
                                }
                                """.formatted(roleId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginEndpointRemainsPublic() throws Exception {
        UUID roleId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email("buyer@orionticket.com")
                .roleId(roleId)
                .build();
        when(loginUserUseCase.login("buyer@orionticket.com", "password123")).thenReturn("jwt");
        when(loginUserUseCase.getUserByEmail("buyer@orionticket.com")).thenReturn(user);
        when(roleRepositoryPort.findById(roleId)).thenReturn(Optional.of(Role.builder()
                .roleId(roleId)
                .name("BUYER")
                .permissions(List.of())
                .build()));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "buyer@orionticket.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk());
    }
}
