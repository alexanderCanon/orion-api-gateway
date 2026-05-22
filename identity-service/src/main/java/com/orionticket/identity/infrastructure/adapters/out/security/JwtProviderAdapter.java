package com.orionticket.identity.infrastructure.adapters.out.security;

import com.orionticket.identity.application.port.out.JwtProviderPort;
import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Component
public class JwtProviderAdapter implements JwtProviderPort {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;
    private final String issuer;
    private final long expirationSeconds;
    private final RoleRepositoryPort roleRepositoryPort;

    public JwtProviderAdapter(
            @Value("${jwt.private-key:${JWT_PRIVATE_KEY:}}") String privateKey,
            @Value("${jwt.public-key:${JWT_PUBLIC_KEY:}}") String publicKey,
            @Value("${jwt.key-id:${JWT_KEY_ID:orionticket-local-key}}") String keyId,
            @Value("${jwt.issuer:${JWT_ISSUER:orionticket-identity}}") String issuer,
            @Value("${jwt.expiration:${JWT_EXPIRATION:86400}}") long expirationSeconds,
            RoleRepositoryPort roleRepositoryPort) {
        this.privateKey = parsePrivateKey(privateKey);
        this.publicKey = parsePublicKey(publicKey);
        this.keyId = keyId;
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
        this.roleRepositoryPort = roleRepositoryPort;
    }

    @Override
    public String generateToken(User user) {
        Role role = roleRepositoryPort.findById(user.getRoleId())
                .orElseThrow(() -> new IllegalStateException("User role not found: " + user.getRoleId()));
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationSeconds * 1000);

        return Jwts.builder()
                .header()
                    .keyId(keyId)
                    .and()
                .issuer(issuer)
                .subject(user.getUserId().toString())
                .claim("email", user.getEmail())
                .claim("roleId", user.getRoleId() != null ? user.getRoleId().toString() : null)
                .claim("role", role.getName())
                .claim("permissions", role.getPermissions() != null ? role.getPermissions() : List.of())
                .claim("organizerId", user.getOrganizerId() != null ? user.getOrganizerId().toString() : null)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public PublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return keyId;
    }

    private static PrivateKey parsePrivateKey(String pem) {
        try {
            byte[] decoded = Base64.getDecoder().decode(stripPem(pem, "PRIVATE KEY"));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA private key configuration", ex);
        }
    }

    private static PublicKey parsePublicKey(String pem) {
        try {
            byte[] decoded = Base64.getDecoder().decode(stripPem(pem, "PUBLIC KEY"));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA public key configuration", ex);
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
