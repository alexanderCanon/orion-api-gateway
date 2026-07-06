package com.orionticket.identity.domain.exception;

/**
 * Se lanza cuando un rol no existe en el sistema.
 */
public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(String message) {
        super(message);
    }
}
