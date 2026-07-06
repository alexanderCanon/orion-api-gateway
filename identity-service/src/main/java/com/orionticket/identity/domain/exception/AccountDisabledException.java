package com.orionticket.identity.domain.exception;

/**
 * Se lanza cuando un usuario intenta autenticarse pero su cuenta no está
 * habilitada para hacerlo (p. ej. {@code SUSPENDED}).
 *
 * <p>El mensaje es deliberadamente genérico para no revelar el estado
 * interno de la cuenta al llamador.</p>
 */
public class AccountDisabledException extends RuntimeException {

    private static final String DEFAULT_MESSAGE =
            "La cuenta no está habilitada para iniciar sesión.";

    public AccountDisabledException() {
        super(DEFAULT_MESSAGE);
    }

    public AccountDisabledException(String message) {
        super(message);
    }
}
