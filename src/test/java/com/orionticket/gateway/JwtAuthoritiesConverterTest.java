package com.orionticket.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthoritiesConverterTest {

    @Test
    void mapsRoleAndPermissionsClaimsToAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .headers(headers -> headers.putAll(Map.of("alg", "RS256")))
                .subject("00000000-0000-0000-0000-000000000001")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("role", "ORGANIZER")
                .claim("permissions", List.of("events:create", "orders:read"))
                .build();

        Collection<GrantedAuthority> authorities = new JwtAuthoritiesConverter().convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ORGANIZER", "events:create", "orders:read");
    }
}
