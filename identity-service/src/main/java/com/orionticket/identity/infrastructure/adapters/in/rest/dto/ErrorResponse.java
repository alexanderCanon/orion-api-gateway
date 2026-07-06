package com.orionticket.identity.infrastructure.adapters.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * Contrato único de error para todas las respuestas no exitosas.
 *
 * <p>Incluye {@code traceId} (tomado del MDC del {@code CorrelationIdFilter})
 * para correlacionar el error del cliente con las entradas de log del
 * servicio. Los campos son deliberadamente estables para no romper
 * consumidores ni el contrato OpenAPI.</p>
 */
@Schema(name = "ErrorResponse", description = "Respuesta de error estándar")
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record ErrorResponse(
        @Schema(description = "Marca de tiempo del error (ISO-8601)", example = "2026-07-05T12:00:00Z")
        OffsetDateTime timestamp,

        @Schema(description = "Código HTTP", example = "404")
        int status,

        @Schema(description = "Categoría de error HTTP", example = "Not Found")
        String error,

        @Schema(description = "Código de error de negocio estable", example = "USER_NOT_FOUND")
        String errorCode,

        @Schema(description = "Mensaje orientado al cliente (sin detalles internos)", example = "Usuario no encontrado")
        String message,

        @Schema(description = "Ruta solicitada", example = "/v1/users/123")
        String path,

        @Schema(description = "Identificador de correlación para trazabilidad", example = "a1b2c3d4-...")
        String traceId
) {
}
