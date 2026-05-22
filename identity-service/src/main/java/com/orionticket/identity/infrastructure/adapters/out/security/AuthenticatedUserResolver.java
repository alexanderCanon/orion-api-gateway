package com.orionticket.identity.infrastructure.adapters.out.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthenticatedUserResolver {

    public AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated JWT is required");
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        String role = jwt.getClaimAsString("role");
        UUID organizerId = resolveOrganizerId(jwt, role, userId);

        return new AuthenticatedUser(userId, role, organizerId);
    }

    public AuthenticatedUser requireOrganizerOwnership(UUID organizerId) {
        AuthenticatedUser currentUser = currentUser();
        if (currentUser.isSuperAdmin()) {
            return currentUser;
        }
        if (!"ORGANIZER".equals(currentUser.role()) || !organizerId.equals(currentUser.organizerId())) {
            throw new AccessDeniedException("Caller cannot manage this organizer");
        }
        return currentUser;
    }

    private static UUID resolveOrganizerId(Jwt jwt, String role, UUID userId) {
        String organizerId = jwt.getClaimAsString("organizerId");
        if (organizerId != null && !organizerId.isBlank()) {
            return UUID.fromString(organizerId);
        }
        return "ORGANIZER".equals(role) ? userId : null;
    }
}
