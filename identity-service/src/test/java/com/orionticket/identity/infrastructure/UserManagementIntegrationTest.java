package com.orionticket.identity.infrastructure;

import com.orionticket.identity.infrastructure.adapters.out.security.AuthenticatedUser;
import com.orionticket.identity.infrastructure.adapters.out.security.AuthenticatedUserResolver;
import com.orionticket.identity.infrastructure.adapters.out.persistence.repository.SpringDataUserRepository;
import com.orionticket.identity.infrastructure.adapters.out.persistence.entity.UserJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@WithMockUser(roles = "SUPER_ADMIN")
class UserManagementIntegrationTest extends IdentityPostgresContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpringDataUserRepository userRepository;

    @MockBean
    private AuthenticatedUserResolver authenticatedUserResolver;

    private UUID testUserId;
    private final UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        when(authenticatedUserResolver.currentUser())
                .thenReturn(new AuthenticatedUser(adminId, "SUPER_ADMIN", null));

        // Creamos un usuario de prueba directamente en la BD
        testUserId = UUID.randomUUID();
        UserJpaEntity user = new UserJpaEntity();
        user.setUserId(testUserId);
        user.setEmail("integration@orionticket.com");
        user.setPasswordHash("hashedpassword");
        user.setFullName("Integration Test");
        user.setPhone("12345678");
        user.setStatus("ACTIVE");
        user.setRoleId(UUID.fromString("00000000-0000-0000-0000-000000000001")); // COMPRADOR role from flyway

        userRepository.save(user);
    }

    @Test
    void shouldSuspendUser() throws Exception {
        mockMvc.perform(put("/v1/users/" + testUserId + "/suspend")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void shouldUpdateUserRole() throws Exception {
        String newRoleId = "00000000-0000-0000-0000-000000000002"; // ORGANIZADOR
        String requestBody = "{\"newRoleId\": \"" + newRoleId + "\"}";

        mockMvc.perform(put("/v1/users/" + testUserId + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(newRoleId));
    }
}
