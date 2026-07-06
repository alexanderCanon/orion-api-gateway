package com.orionticket.identity.infrastructure.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración de claves JWT con soporte para rotación.
 *
 * <p>Soporta dos modos:</p>
 * <ul>
 *   <li><b>Modo simple (backward-compat):</b> {@code jwt.private-key},
 *       {@code jwt.public-key}, {@code jwt.key-id} — una sola clave activa.</li>
 *   <li><b>Modo multi-clave (rotación):</b> {@code jwt.keys[]} con
 *       {@code kid}, {@code private-key}, {@code public-key}, {@code active}.
 *       La clave marcada como {@code active: true} se usa para firmar;
 *       todas las claves se usan para validar.</li>
 * </ul>
 *
 * <p>Procedimiento de rotación sin downtime:</p>
 * <ol>
 *   <li>Agregar la clave nueva al final de {@code jwt.keys[]} con
 *       {@code active: false}.</li>
 *   <li>Esperar al menos el TTL máximo del access token (15 min).</li>
 *   <li>Marcar la clave nueva como {@code active: true} y la vieja como
 *       {@code active: false}. Reiniciar el servicio.</li>
 *   <li>Esperar a que todos los tokens firmados con la clave vieja expiren
 *       (TTL del access token).</li>
 *   <li>Remover la clave vieja de {@code jwt.keys[]}.</li>
 * </ol>
 */
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtKeyProperties {

    /** Clave privada en formato PEM (modo simple / backward-compat). */
    private String privateKey;

    /** Clave pública en formato PEM (modo simple / backward-compat). */
    private String publicKey;

    /** Identificador de clave (modo simple / backward-compat). */
    private String keyId = "orionticket-local-key";

    /** Issuer del JWT. */
    private String issuer = "orionticket-identity";

    /** TTL del access token en segundos. */
    private long expiration = 900;

    /** TTL del refresh token en segundos. */
    private long refreshExpiration = 2592000;

    /** Lista de claves para rotación (modo multi-clave). */
    private List<KeyEntry> keys = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class KeyEntry {
        /** Identificador único de la clave (kid en JWT header). */
        private String kid;
        /** Clave privada PEM. */
        private String privateKey;
        /** Clave pública PEM. */
        private String publicKey;
        /** Si es true, esta clave se usa para firmar nuevos tokens. */
        private boolean active;
    }
}
