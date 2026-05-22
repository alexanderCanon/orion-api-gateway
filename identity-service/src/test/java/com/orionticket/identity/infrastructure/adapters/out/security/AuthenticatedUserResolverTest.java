package com.orionticket.identity.infrastructure.adapters.out.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticatedUserResolverTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserUsesSubjectAsOrganizerIdWhenOrganizerClaimIsMissing() {
        UUID userId = UUID.randomUUID();
        authenticate(jwt(userId, "ORGANIZER", null));

        AuthenticatedUser currentUser = new AuthenticatedUserResolver().currentUser();

        assertEquals(userId, currentUser.userId());
        assertEquals("ORGANIZER", currentUser.role());
        assertEquals(userId, currentUser.organizerId());
    }

    @Test
    void requireOrganizerOwnershipRejectsDifferentOrganizerForOrganizerRole() {
        UUID organizerId = UUID.randomUUID();
        authenticate(jwt(UUID.randomUUID(), "ORGANIZER", organizerId));

        assertThrows(AccessDeniedException.class,
                () -> new AuthenticatedUserResolver().requireOrganizerOwnership(UUID.randomUUID()));
    }

    @Test
    void requireOrganizerOwnershipAllowsSuperAdmin() {
        authenticate(jwt(UUID.randomUUID(), "SUPER_ADMIN", null));

        assertDoesNotThrow(() -> new AuthenticatedUserResolver().requireOrganizerOwnership(UUID.randomUUID()));
    }

    private static void authenticate(Jwt jwt) {
        AbstractAuthenticationToken authentication = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Jwt jwt(UUID userId, String role, UUID organizerId) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (organizerId != null) {
            builder.claim("organizerId", organizerId.toString());
        }
        return builder.build();
    }
}
