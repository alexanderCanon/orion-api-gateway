package com.orionticket.identity.infrastructure.config;

import com.orionticket.identity.infrastructure.adapters.out.security.JwtAuthoritiesConverter;
import com.orionticket.identity.infrastructure.adapters.out.security.JwtProviderAdapter;
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

    @Bean
    public JwtDecoder jwtDecoder(JwtProviderAdapter jwtProviderAdapter) {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) jwtProviderAdapter.publicKey()).build();
    }
}
