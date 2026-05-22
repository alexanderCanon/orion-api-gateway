package com.orionticket.identity.infrastructure.adapters.out.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthoritiesConverterTest {

    @Test
    void convertMapsRoleAndPermissionsToSpringAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("00000000-0000-0000-0000-000000000001")
                .claim("role", "ORGANIZER")
                .claim("permissions", List.of("events:create", "staff:create:own"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        List<String> authorities = new JwtAuthoritiesConverter().convert(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertTrue(authorities.contains("ROLE_ORGANIZER"));
        assertTrue(authorities.contains("events:create"));
        assertTrue(authorities.contains("staff:create:own"));
    }
}
