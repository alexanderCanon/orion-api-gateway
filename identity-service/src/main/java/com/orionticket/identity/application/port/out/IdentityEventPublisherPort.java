package com.orionticket.identity.application.port.out;

import com.orionticket.identity.domain.model.User;

public interface IdentityEventPublisherPort {
    void publishStaffCreated(User staff);
}
