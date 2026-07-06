package com.orionticket.identity.domain.exception;

/**
 * Se lanza cuando una cuenta está temporalmente bloqueada por exceder el
 * umbral de intentos de login fallidos (Fase 2, C4 — protección contra
 * fuerza bruta).
 *
 * <p>Lleva la duración restante del bloqueo en segundos para que el
 * handler REST pueda emitir el header {@code Retry-After}.</p>
 */
public class AccountLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public AccountLockedException(long retryAfterSeconds) {
        super("Demasiados intentos fallidos. La cuenta está temporalmente bloqueada.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
