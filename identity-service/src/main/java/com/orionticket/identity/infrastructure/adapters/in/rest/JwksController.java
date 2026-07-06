package com.orionticket.identity.infrastructure.adapters.in.rest;

import com.orionticket.identity.infrastructure.config.JwtKeyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwtKeyManager keyManager;

    @GetMapping("/.well-known/jwks.json")
    public JwksResponse getJwks() {
        Map<String, RSAPublicKey> publicKeys = keyManager.allPublicKeys();
        List<JwksResponse.Jwk> jwks = new java.util.ArrayList<>(publicKeys.size());

        publicKeys.forEach((kid, publicKey) -> jwks.add(new JwksResponse.Jwk(
                "RSA",
                "sig",
                kid,
                "RS256",
                base64Url(publicKey.getModulus().toByteArray()),
                base64Url(publicKey.getPublicExponent().toByteArray())
        )));

        return new JwksResponse(jwks);
    }

    private static String base64Url(byte[] value) {
        int offset = value.length > 1 && value[0] == 0 ? 1 : 0;
        byte[] unsigned = java.util.Arrays.copyOfRange(value, offset, value.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned);
    }
}
