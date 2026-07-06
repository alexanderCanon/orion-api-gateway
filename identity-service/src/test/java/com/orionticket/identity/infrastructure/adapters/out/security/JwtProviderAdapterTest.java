package com.orionticket.identity.infrastructure.adapters.out.security;

import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import com.orionticket.identity.infrastructure.config.JwtKeyManager;
import com.orionticket.identity.infrastructure.config.JwtKeyProperties;
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

        JwtKeyProperties props = singleKeyProps(
                pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()),
                pem("PUBLIC KEY", keyPair.getPublic().getEncoded()),
                "orion-key-1");
        JwtKeyManager keyManager = new JwtKeyManager(props);
        JwtProviderAdapter adapter = new JwtProviderAdapter(keyManager, roles);

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

    @Test
    void generateTokenWithMultipleKeysSignsWithActiveKey() throws Exception {
        // Fase 5.4: con múltiples claves, el token se firma con la clave activa.
        KeyPair activeKeyPair = rsaKeyPair();
        KeyPair oldKeyPair = rsaKeyPair();
        UUID roleId = UUID.randomUUID();
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email("test@orionticket.com")
                .roleId(roleId)
                .build();
        RoleRepositoryPort roles = roleRepository(Role.builder()
                .roleId(roleId)
                .name("BUYER")
                .permissions(List.of())
                .build());

        JwtKeyProperties props = new JwtKeyProperties();
        props.setIssuer("orionticket-identity");
        props.setExpiration(3600);
        props.setKeys(List.of(
                keyEntry("old-key", pem("PRIVATE KEY", oldKeyPair.getPrivate().getEncoded()),
                        pem("PUBLIC KEY", oldKeyPair.getPublic().getEncoded()), false),
                keyEntry("active-key", pem("PRIVATE KEY", activeKeyPair.getPrivate().getEncoded()),
                        pem("PUBLIC KEY", activeKeyPair.getPublic().getEncoded()), true)
        ));
        JwtKeyManager keyManager = new JwtKeyManager(props);
        JwtProviderAdapter adapter = new JwtProviderAdapter(keyManager, roles);

        String token = adapter.generateToken(user);

        // El token debe tener kid="active-key" y validar con la clave activa.
        Jws<Claims> parsedToken = Jwts.parser()
                .verifyWith((RSAPublicKey) activeKeyPair.getPublic())
                .requireIssuer("orionticket-identity")
                .build()
                .parseSignedClaims(token);

        assertEquals("active-key", parsedToken.getHeader().getKeyId());
        assertEquals(user.getUserId().toString(), parsedToken.getPayload().getSubject());
    }

    private static JwtKeyProperties singleKeyProps(String privateKey, String publicKey, String keyId) {
        JwtKeyProperties props = new JwtKeyProperties();
        props.setPrivateKey(privateKey);
        props.setPublicKey(publicKey);
        props.setKeyId(keyId);
        props.setIssuer("orionticket-identity");
        props.setExpiration(3600);
        return props;
    }

    private static JwtKeyProperties.KeyEntry keyEntry(String kid, String priv, String pub, boolean active) {
        JwtKeyProperties.KeyEntry entry = new JwtKeyProperties.KeyEntry();
        entry.setKid(kid);
        entry.setPrivateKey(priv);
        entry.setPublicKey(pub);
        entry.setActive(active);
        return entry;
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
            public Optional<Role> findByName(String name) {
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
