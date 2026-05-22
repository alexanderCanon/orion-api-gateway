package com.orionticket.identity.infrastructure.adapters.out.security;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String role,
        UUID organizerId
) {

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(role);
    }
}
