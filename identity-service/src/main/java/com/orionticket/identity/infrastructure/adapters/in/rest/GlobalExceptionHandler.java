package com.orionticket.identity.infrastructure.adapters.in.rest;

import com.orionticket.identity.domain.exception.AccountDisabledException;
import com.orionticket.identity.domain.exception.AccountLockedException;
import com.orionticket.identity.domain.exception.InvalidCredentialsException;
import com.orionticket.identity.domain.exception.RoleNotAllowedException;
import com.orionticket.identity.domain.exception.RoleNotFoundException;
import com.orionticket.identity.domain.exception.UserAlreadyExistsException;
import com.orionticket.identity.domain.exception.UserNotFoundException;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.ErrorResponse;
import com.orionticket.identity.infrastructure.adapters.in.rest.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones.
 *
 * <p>Contrato de error unificado vía {@link ErrorResponse}. Las excepciones
 * internas (500) devuelven un mensaje genérico al cliente y loguean el
 * detalle completo con el {@code traceId} para investigación.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String GENERIC_INTERNAL_ERROR_MESSAGE =
            "Ocurrió un error interno. Contacta al soporte si el problema persiste.";

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request);
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ErrorResponse> handleAccountDisabled(AccountDisabledException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", ex.getMessage(), request);
    }

    /**
     * Cuenta bloqueada por exceder el umbral de intentos fallidos (C4).
     * Devuelve 429 Too Many Requests con el header {@code Retry-After}
     * indicando cuántos segundos faltan para que expire el bloqueo.
     */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException ex, HttpServletRequest request) {
        long retryAfter = Math.max(1L, ex.getRetryAfterSeconds());
        ResponseEntity<ErrorResponse> response = build(HttpStatus.TOO_MANY_REQUESTS,
                "ACCOUNT_LOCKED", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfter))
                .body(response.getBody());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoleNotFound(RoleNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(RoleNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleRoleNotAllowed(RoleNotAllowedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "ROLE_NOT_ALLOWED", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a));
        String message = "Solicitud inválida: " + fieldErrors;
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permisos para realizar esta acción.", request);
    }

    /**
     * Fallback para cualquier {@link RuntimeException} no capturada arriba.
     * Devuelve un mensaje genérico al cliente y loguea el detalle con el
     * traceId para investigación interna. Nunca expone {@code ex.getMessage()}
     * al exterior.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        log.error("Unhandled RuntimeException [traceId={}]: {} - {}",
                currentTraceId(), ex.getClass().getName(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                GENERIC_INTERNAL_ERROR_MESSAGE, request);
    }

    /**
     * Fallback último para cualquier otra {@link Throwable}.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled Exception [traceId={}]: {} - {}",
                currentTraceId(), ex.getClass().getName(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                GENERIC_INTERNAL_ERROR_MESSAGE, request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String errorCode, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .errorCode(errorCode)
                .message(message)
                .path(request.getRequestURI())
                .traceId(currentTraceId())
                .build();
        return ResponseEntity.status(status).body(body);
    }

    private static String currentTraceId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
