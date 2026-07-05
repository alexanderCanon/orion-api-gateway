package com.orionticket.identity.infrastructure.adapters.out.audit;

import com.orionticket.identity.application.port.out.AuditLogPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adaptador SLF4J de {@link AuditLogPort}.
 *
 * <p>Registra eventos de auditoría como INFO estructurado con IP y
 * user-agent cuando están disponibles. En mediano plazo esto debería
 * persistir en una tabla {@code audit_log} o publicarse a RabbitMQ
 * para no depender de la rotación de logs (ver ADR-012).</p>
 */
@Slf4j
@Component
public class Slf4jAuditLogAdapter implements AuditLogPort {

    @Override
    public void logAction(UUID actorId, String action, String details, String ipAddress, String userAgent) {
        log.info("AUDIT_LOG | ActorID: {} | Action: {} | Details: {} | IP: {} | UA: {}",
                actorId, action, details,
                ipAddress != null ? ipAddress : "-",
                userAgent != null ? userAgent : "-");
    }
}
