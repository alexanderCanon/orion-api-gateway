package com.orionticket.identity.application.port.out;

/**
 * Genera un token opaco aleatorio para refresh.
 *
 * <p>El token en claro se devuelve al cliente y se hashea (SHA-256) antes
 * de persistirlo. La implementación debe usar {@link java.security.SecureRandom}
 * con al menos 256 bits de entropía.</p>
 */
public interface RefreshTokenGeneratorPort {

    /** Genera un token opaco aleatorio en Base64URL (sin padding). */
    String generate();

    /** Hashea el token opaco con SHA-256 y lo devuelve en hexadecimal (64 chars). */
    String hash(String rawToken);
}
