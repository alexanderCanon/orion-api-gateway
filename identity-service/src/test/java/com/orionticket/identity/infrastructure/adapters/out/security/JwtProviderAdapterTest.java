package com.orionticket.identity.infrastructure.adapters.out.security;

import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class JwtProviderAdapterTest {

    @Test
    void generateTokenSignsWithRsaAndIncludesSecurityClaims() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        UUID roleId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email("organizer@orionticket.com")
                .roleId(roleId)
                .organizerId(organizerId)
                .build();
        RoleRepositoryPort roles = roleRepository(Role.builder()
                .roleId(roleId)
                .name("ORGANIZER")
                .permissions(List.of("events:create", "events:update"))
                .build());

        JwtProviderAdapter adapter = new JwtProviderAdapter(
                privateKeyPem(keyPair),
                publicKeyPem(keyPair),
                "orion-key-1",
                "orionticket-identity",
                3600,
                roles
        );

        String token = adapter.generateToken(user);

        Jws<Claims> parsedToken = Jwts.parser()
                .verifyWith((RSAPublicKey) keyPair.getPublic())
                .requireIssuer("orionticket-identity")
                .build()
                .parseSignedClaims(token);

        Claims claims = parsedToken.getPayload();
        assertEquals("orion-key-1", parsedToken.getHeader().getKeyId());
        assertEquals(user.getUserId().toString(), claims.getSubject());
        assertEquals(user.getEmail(), claims.get("email", String.class));
        assertEquals(roleId.toString(), claims.get("roleId", String.class));
        assertEquals("ORGANIZER", claims.get("role", String.class));
        assertEquals(organizerId.toString(), claims.get("organizerId", String.class));
        assertIterableEquals(List.of("events:create", "events:update"), claims.get("permissions", List.class));
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String privateKeyPem(KeyPair keyPair) {
        return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
    }

    private static String publicKeyPem(KeyPair keyPair) {
        return pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----";
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
