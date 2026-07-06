package com.orionticket.identity.infrastructure.adapters.out.security;

import com.orionticket.identity.application.port.out.JwtProviderPort;
import com.orionticket.identity.infrastructure.config.JwtKeyManager;
import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.util.Date;
import java.util.List;

@Component
public class JwtProviderAdapter implements JwtProviderPort {

    private final JwtKeyManager keyManager;
    private final RoleRepositoryPort roleRepositoryPort;

    public JwtProviderAdapter(JwtKeyManager keyManager, RoleRepositoryPort roleRepositoryPort) {
        this.keyManager = keyManager;
        this.roleRepositoryPort = roleRepositoryPort;
    }

    @Override
    public String generateToken(User user) {
        Role role = roleRepositoryPort.findById(user.getRoleId())
                .orElseThrow(() -> new IllegalStateException("User role not found: " + user.getRoleId()));
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + keyManager.expirationSeconds() * 1000);

        JwtKeyManager.KeyMaterial activeKey = keyManager.activeKey();
        PrivateKey privateKey = activeKey.privateKey();

        return Jwts.builder()
                .header()
                    .keyId(activeKey.kid())
                    .and()
                .issuer(keyManager.issuer())
                .subject(user.getUserId().toString())
                .claim("email", user.getEmail())
                .claim("email_verified", user.isActive())
                .claim("roleId", user.getRoleId() != null ? user.getRoleId().toString() : null)
                .claim("role", role.getName())
                .claim("permissions", role.getPermissions() != null ? role.getPermissions() : List.of())
                .claim("organizerId", user.getOrganizerId() != null ? user.getOrganizerId().toString() : null)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Devuelve la clave pública de la clave activa.
     * @deprecated Usar {@link JwtKeyManager#allPublicKeys()} para soporte multi-clave.
     */
    @Deprecated(since = "5.4", forRemoval = true)
    public java.security.PublicKey publicKey() {
        return keyManager.activeKey().publicKey();
    }

    /**
     * Devuelve el kid de la clave activa.
     * @deprecated Usar {@link JwtKeyManager#activeKeyId()}.
     */
    @Deprecated(since = "5.4", forRemoval = true)
    public String keyId() {
        return keyManager.activeKeyId();
    }
}
