package com.orionticket.identity.application.port.out;

import com.orionticket.identity.domain.model.User;

/**
 * Puerto de publicación de eventos de identidad hacia el bus de mensajes
 * (RabbitMQ). El notification-service consume estos eventos para enviar
 * los emails correspondientes.
 */
public interface IdentityEventPublisherPort {

    void publishStaffCreated(User staff);

    /**
     * Publica un evento solicitando el envío del email de verificación
     * de email. El token en claro va en el evento para que el
     * notification-service pueda construir el link de verificación.
     */
    void publishEmailVerificationRequested(User user, String rawToken);

    /**
     * Publica un evento solicitando el envío del email de recuperación
     * de contraseña. El token en claro va en el evento para que el
     * notification-service pueda construir el link de reset.
     */
    void publishPasswordRecoveryRequested(User user, String rawToken);
}
