package com.orionticket.identity.infrastructure.adapters.in.rest;

import com.orionticket.identity.infrastructure.config.JwtKeyManager;
import com.orionticket.identity.infrastructure.config.JwtKeyProperties;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwksControllerTest {

    @Test
    void jwksContainsPublicRsaKeyWithKeyId() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        JwtKeyProperties props = singleKeyProps(
                pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()),
                pem("PUBLIC KEY", keyPair.getPublic().getEncoded()),
                "orion-key-1");
        JwtKeyManager keyManager = new JwtKeyManager(props);
        JwksController controller = new JwksController(keyManager);

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

    @Test
    void jwksExposesAllKeysInRotationMode() throws Exception {
        // Fase 5.4: en modo rotación, el JWKS debe exponer todas las claves
        // públicas, no solo la activa.
        KeyPair activeKeyPair = rsaKeyPair();
        KeyPair oldKeyPair = rsaKeyPair();

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
        JwksController controller = new JwksController(keyManager);

        JwksResponse response = controller.getJwks();

        assertEquals(2, response.keys().size());
        // Ambas claves deben estar presentes (el JWKS expone todas para validación)
        assertTrue(response.keys().stream().anyMatch(j -> "old-key".equals(j.kid())));
        assertTrue(response.keys().stream().anyMatch(j -> "active-key".equals(j.kid())));
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
}
