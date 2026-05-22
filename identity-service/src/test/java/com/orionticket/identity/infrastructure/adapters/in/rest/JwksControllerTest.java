package com.orionticket.identity.infrastructure.adapters.in.rest;

import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import com.orionticket.identity.infrastructure.adapters.out.security.JwtProviderAdapter;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwksControllerTest {

    @Test
    void jwksContainsPublicRsaKeyWithKeyId() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        JwtProviderAdapter jwtProvider = new JwtProviderAdapter(
                pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()),
                pem("PUBLIC KEY", keyPair.getPublic().getEncoded()),
                "orion-key-1",
                "orionticket-identity",
                3600,
                emptyRoles()
        );
        JwksController controller = new JwksController(jwtProvider);

        JwksResponse response = controller.getJwks();

        assertEquals(1, response.keys().size());
        JwksResponse.Jwk key = response.keys().getFirst();
        assertEquals("orion-key-1", key.kid());
        assertEquals("RSA", key.kty());
        assertEquals("sig", key.use());
        assertEquals("RS256", key.alg());
        assertNotNull(key.n());
        assertNotNull(key.e());
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }

    private static RoleRepositoryPort emptyRoles() {
        return new RoleRepositoryPort() {
            @Override
            public com.orionticket.identity.domain.model.Role save(com.orionticket.identity.domain.model.Role role) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<com.orionticket.identity.domain.model.Role> findById(java.util.UUID roleId) {
                return Optional.empty();
            }

            @Override
            public List<com.orionticket.identity.domain.model.Role> findAll() {
                return List.of();
            }

            @Override
            public void deleteById(java.util.UUID roleId) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
