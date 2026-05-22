package com.orionticket.identity.infrastructure.adapters.in.rest;

import com.orionticket.identity.infrastructure.adapters.out.security.JwtProviderAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwtProviderAdapter jwtProviderAdapter;

    @GetMapping("/.well-known/jwks.json")
    public JwksResponse getJwks() {
        RSAPublicKey publicKey = (RSAPublicKey) jwtProviderAdapter.publicKey();
        JwksResponse.Jwk jwk = new JwksResponse.Jwk(
                "RSA",
                "sig",
                jwtProviderAdapter.keyId(),
                "RS256",
                base64Url(publicKey.getModulus().toByteArray()),
                base64Url(publicKey.getPublicExponent().toByteArray())
        );
        return new JwksResponse(List.of(jwk));
    }

    private static String base64Url(byte[] value) {
        int offset = value.length > 1 && value[0] == 0 ? 1 : 0;
        byte[] unsigned = java.util.Arrays.copyOfRange(value, offset, value.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned);
    }
}
