package com.orionticket.identity.infrastructure.adapters.out.audit;

import com.orionticket.identity.application.port.out.AuditLogPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class Slf4jAuditLogAdapter implements AuditLogPort {

    @Override
    public void logAction(UUID adminId, String action, String details) {
        // Para el MVP, y dado que AuditLog es cross-cutting sin DB definida aún (ADR-012),
        // registramos como INFO estructurado. Más adelante puede ser enviado por RabbitMQ
        // o guardado en el datastore centralizado.
        log.info("AUDIT_LOG | ActorID: {} | Action: {} | Details: {}", adminId, action, details);
    }
}
