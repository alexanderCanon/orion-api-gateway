package com.orionticket.identity.infrastructure.adapters.in.rest.filter;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Filtro de rate limiting por IP para los endpoints públicos de autenticación
 * (Fase 2, C4 — defensa en profundidad contra fuerza bruta y credential
 * stuffing).
 *
 * <p>Usa Bucket4j con un bucket token-bucket en memoria por IP. La defensa
 * principal contra fuerza bruta es el lockout por cuenta (ver
 * {@code LoginUserService}); este filtro complementa limitando el número de
 * peticiones por IP independientemente de la cuenta objetivo — útil contra
 * ataques distribuidos sobre múltiples emails.</p>
 *
 * <p>La IP se obtiene de {@code X-Forwarded-For} (el servicio tiene
 * {@code forward-headers-strategy: native}) con fallback a
 * {@code getRemoteAddr()}.</p>
 *
 * <p><strong>Limitación:</strong> el estado es en memoria; con múltiples
 * réplicas del servicio cada una mantiene su propio contador. El rate
 * limiting real por IP debe vivir en el gateway (Traefik
 * {@code rateLimit} middleware). Esto es defensa en profundidad.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String[] PROTECTED_PATHS = {
            "/v1/auth/login",
            "/v1/auth/register",
            "/v1/auth/recover",
            "/v1/auth/resend-verification"
    };

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${security.rate-limit.capacity:10}")
    private int capacity;

    @Value("${security.rate-limit.refill-minutes:1}")
    private int refillMinutes;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!isProtectedPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(clientIp, this::newBucket);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            long retryAfter = bucket.getAvailableTokens() == 0
                    ? refillMinutes * 60L
                    : 1L;
            writeRateLimitResponse(response, retryAfter);
        }
    }

    private boolean isProtectedPath(String path) {
        for (String protectedPath : PROTECTED_PATHS) {
            if (protectedPath.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private Bucket newBucket(String key) {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(capacity)
                        .refillIntervally(capacity, Duration.ofMinutes(refillMinutes)))
                .build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitResponse(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(Math.max(1L, retryAfterSeconds)));

        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String body = """
                {
                  "timestamp": "%s",
                  "status": 429,
                  "error": "Too Many Requests",
                  "errorCode": "RATE_LIMIT_EXCEEDED",
                  "message": "Demasiadas peticiones. Inténtalo de nuevo más tarde.",
                  "path": null,
                  "traceId": "%s"
                }""".formatted(OffsetDateTime.now(), traceId == null ? "null" : traceId);

        response.getWriter().write(body);
        response.getWriter().flush();
    }
}
