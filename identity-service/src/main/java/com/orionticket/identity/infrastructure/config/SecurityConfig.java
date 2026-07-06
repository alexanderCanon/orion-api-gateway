package com.orionticket.identity.infrastructure.config;

import com.orionticket.identity.infrastructure.adapters.out.security.JwtAuthoritiesConverter;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtKeyProperties.class)
public class SecurityConfig {

    /**
     * Password encoder delegante: usa bcrypt strength 12 como default para
     * nuevos hashes, pero puede validar hashes de otros formatos (prefijo
     * `{bcrypt}`, `{argon2}`, etc.). Esto permite migrar a Argon2id en el
     * futuro sin romper los hashes existentes.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));
        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder(
                "bcrypt", encoders);
        // Fallback: si un hash no tiene prefijo, asumir bcrypt (compat con
        // los hashes existentes generados antes de esta fase).
        delegating.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder(12));
        return delegating;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthoritiesConverter authoritiesConverter) throws Exception {
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            // Sesión stateless: no crear HttpSession, no usar cookies de sesión.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Headers de seguridad: HSTS, X-Content-Type-Options, Cache-Control.
            .headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .contentTypeOptions(Customizer.withDefaults())
                .cacheControl(Customizer.withDefaults())
            )
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos de autenticación: rutas EXPLÍCITAS (no wildcard)
                // para evitar exponer por accidente endpoints futuros bajo /v1/auth/.
                .requestMatchers("/v1/auth/register", "/v1/auth/login",
                                 "/v1/auth/refresh", "/v1/auth/logout",
                                 "/v1/auth/recover", "/v1/auth/recover/confirm",
                                 "/v1/auth/verify", "/v1/auth/resend-verification").permitAll()
                // change-password requiere autenticación (JWT) — no está en permitAll.
                .requestMatchers("/.well-known/jwks.json").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health").permitAll() // Salud para monitoreo
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
            );
        // CORS: lo maneja el API Gateway (Traefik) en producción. En desarrollo
        // Spring Security usa la config por defecto. No se define CorsConfigurationSource
        // aquí para no duplicar la lógica del gateway.
        return http.build();
    }

    /**
     * JwtDecoder que valida contra todas las claves públicas configuradas
     * en {@link JwtKeyManager}, seleccionando la clave por el {@code kid}
     * del header del JWT. Esto soporta rotación de claves: los tokens
     * firmados con una clave vieja siguen validando hasta que la clave se
     * retire de la configuración.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtKeyManager keyManager) {
        // Si solo hay una clave, usar el path simple (withPublicKey) que
        // no requiere nimbus-jose-jwt JWKSet.
        if (keyManager.keyCount() == 1) {
            RSAPublicKey publicKey = (RSAPublicKey) keyManager.activeKey().publicKey();
            return NimbusJwtDecoder.withPublicKey(publicKey).build();
        }

        // Multi-clave: construir un JWKSet con todas las claves públicas y
        // un JWSVerificationKeySelector que selecciona por kid.
        java.util.List<JWK> jwks = new java.util.ArrayList<>();
        keyManager.allPublicKeys().forEach((kid, rsaPublicKey) ->
                jwks.add(new RSAKey.Builder(rsaPublicKey).keyID(kid).build()));
        JWKSet jwkSet = new JWKSet(jwks);
        JWSVerificationKeySelector<com.nimbusds.jose.proc.SecurityContext> keySelector =
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new ImmutableJWKSet<>(jwkSet));
        DefaultJWTProcessor<com.nimbusds.jose.proc.SecurityContext> processor =
                new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(keySelector);
        return new NimbusJwtDecoder(processor);
    }
}
