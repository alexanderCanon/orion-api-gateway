package com.orionticket.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "IDENTITY_SERVICE_URL=forward:/__test-upstream",
        "EVENT_MANAGEMENT_SERVICE_URL=forward:/__test-upstream",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=orionticket-identity",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=forward:/__test-jwks"
})
class SecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    @Test
    void authLoginRouteDoesNotRequireJwt() {
        webTestClient.post()
                .uri("/v1/auth/login")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void actuatorHealthDoesNotRequireJwt() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void catalogRouteDoesNotRequireJwt() {
        webTestClient.get()
                .uri("/v1/catalog/events")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void protectedRouteWithoutJwtReturnsUnauthorized() {
        webTestClient.get()
                .uri("/v1/events")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteWithJwtPassesGatewayAuthentication() {
        Jwt jwt = Jwt.withTokenValue("valid-token")
                .headers(headers -> headers.putAll(Map.of("alg", "RS256")))
                .subject("00000000-0000-0000-0000-000000000001")
                .issuer("orionticket-identity")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("role", "BUYER")
                .build();
        when(jwtDecoder.decode("valid-token")).thenReturn(Mono.just(jwt));

        webTestClient.get()
                .uri("/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .exchange()
                .expectStatus().isNotFound();
    }
}
