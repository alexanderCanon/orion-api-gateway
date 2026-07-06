package com.orionticket.identity.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centraliza la gestión de claves RSA para firma y validación de JWT.
 *
 * <p>Mantiene un mapa de {@code kid → KeyPair} y un {@code activeKeyId}.
 * Si solo hay una clave (modo simple / backward-compat), esa es la activa.
 * Si hay múltiples claves (modo rotación), la marcada como {@code active}
 * se usa para firmar y todas para validar.</p>
 */
@Slf4j
@Component
public class JwtKeyManager {

    private final Map<String, KeyMaterial> keys = new LinkedHashMap<>();
    private final String activeKeyId;
    private final String issuer;
    private final long expirationSeconds;

    public JwtKeyManager(JwtKeyProperties props) {
        this.issuer = props.getIssuer();
        this.expirationSeconds = props.getExpiration();

        if (props.getKeys() != null && !props.getKeys().isEmpty()) {
            // Modo multi-clave (rotación)
            String active = null;
            for (JwtKeyProperties.KeyEntry entry : props.getKeys()) {
                PrivateKey priv = parsePrivateKey(entry.getPrivateKey(), entry.getKid());
                PublicKey pub = parsePublicKey(entry.getPublicKey(), entry.getKid());
                keys.put(entry.getKid(), new KeyMaterial(entry.getKid(), priv, pub));
                if (entry.isActive()) {
                    if (active != null) {
                        throw new IllegalStateException(
                                "Multiple active JWT keys configured; only one can be active at a time");
                    }
                    active = entry.getKid();
                }
            }
            if (active == null) {
                throw new IllegalStateException(
                        "No active JWT key configured; exactly one key must have active: true");
            }
            this.activeKeyId = active;
            log.info("JWT key rotation enabled: {} key(s) loaded, active kid='{}'",
                    keys.size(), activeKeyId);
        } else {
            // Modo simple (backward-compat)
            String kid = props.getKeyId();
            PrivateKey priv = parsePrivateKey(props.getPrivateKey(), kid);
            PublicKey pub = parsePublicKey(props.getPublicKey(), kid);
            keys.put(kid, new KeyMaterial(kid, priv, pub));
            this.activeKeyId = kid;
            log.info("JWT single-key mode: kid='{}'", kid);
        }
    }

    /** Devuelve el material criptográfico de la clave activa (para firmar). */
    public KeyMaterial activeKey() {
        return keys.get(activeKeyId);
    }

    /** Devuelve la clave pública para el kid dado, o null si no existe. */
    public PublicKey publicKeyFor(String kid) {
        KeyMaterial km = keys.get(kid);
        return km != null ? km.publicKey() : null;
    }

    /** Devuelve un mapa inmutable de kid → RSAPublicKey para todas las claves. */
    public Map<String, RSAPublicKey> allPublicKeys() {
        Map<String, RSAPublicKey> result = new LinkedHashMap<>();
        keys.forEach((kid, km) -> result.put(kid, (RSAPublicKey) km.publicKey()));
        return Collections.unmodifiableMap(result);
    }

    public String activeKeyId() {
        return activeKeyId;
    }

    public String issuer() {
        return issuer;
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    public int keyCount() {
        return keys.size();
    }

    /** Registro interno de material criptográfico por clave. */
    public record KeyMaterial(String kid, PrivateKey privateKey, PublicKey publicKey) {}

    private static PrivateKey parsePrivateKey(String pem, String kid) {
        try {
            byte[] decoded = Base64.getDecoder().decode(stripPem(pem, "PRIVATE KEY"));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA private key for kid='" + kid + "'", ex);
        }
    }

    private static PublicKey parsePublicKey(String pem, String kid) {
        try {
            byte[] decoded = Base64.getDecoder().decode(stripPem(pem, "PUBLIC KEY"));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA public key for kid='" + kid + "'", ex);
        }
    }

    private static String stripPem(String pem, String type) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException(type + " is required");
        }
        return pem.replace("\\n", "\n")
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
    }
}
