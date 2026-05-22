package com.orionticket.identity.infrastructure;

import com.orionticket.identity.infrastructure.adapters.out.persistence.repository.SpringDataUserRepository;
import com.orionticket.identity.application.port.out.IdentityEventPublisherPort;
import com.orionticket.identity.infrastructure.adapters.out.security.AuthenticatedUser;
import com.orionticket.identity.infrastructure.adapters.out.security.AuthenticatedUserResolver;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@WithMockUser(roles = "ORGANIZER")
class OrganizerStaffIntegrationTest extends IdentityPostgresContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpringDataUserRepository userRepository;

    @MockBean
    private IdentityEventPublisherPort eventPublisherPort;

    @MockBean
    private AuthenticatedUserResolver authenticatedUserResolver;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        when(authenticatedUserResolver.requireOrganizerOwnership(any(UUID.class)))
                .thenAnswer(invocation -> new AuthenticatedUser(UUID.randomUUID(), "ORGANIZER", invocation.getArgument(0)));
    }

    @Test
    void shouldCreateDoorValidatorSuccessfully() throws Exception {
        UUID organizerId = UUID.randomUUID();
        String doorValidatorRoleId = "00000000-0000-0000-0000-000000000005";
        
        String requestBody = """
                {
                    "email": "validador@test.com",
                    "password": "password123",
                    "fullName": "Juan Validador",
                    "phone": "555-0101",
                    "roleId": "%s"
                }
                """.formatted(doorValidatorRoleId);

        mockMvc.perform(post("/v1/organizers/" + organizerId + "/staff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("validador@test.com"))
                .andExpect(jsonPath("$.roleId").value(doorValidatorRoleId))
                .andExpect(jsonPath("$.organizerId").value(organizerId.toString()));
    }

    @Test
    void shouldFailWhenRoleIsNotAllowed() throws Exception {
        UUID organizerId = UUID.randomUUID();
        String adminRoleId = "00000000-0000-0000-0000-000000000003"; // ADMIN no permitido aquí
        
        String requestBody = """
                {
                    "email": "hacker@test.com",
                    "password": "password123",
                    "fullName": "Intento de Admin",
                    "roleId": "%s"
                }
                """.formatted(adminRoleId);

        mockMvc.perform(post("/v1/organizers/" + organizerId + "/staff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isUnprocessableEntity()); 
    }
}
