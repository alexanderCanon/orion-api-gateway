package com.orionticket.identity.application.port.out;

import java.util.UUID;

public interface AuditLogPort {
    void logAction(UUID adminId, String action, String details);
}
