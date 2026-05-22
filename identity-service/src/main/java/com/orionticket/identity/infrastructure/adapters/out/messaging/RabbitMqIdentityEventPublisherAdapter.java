package com.orionticket.identity.infrastructure.adapters.out.messaging;

import com.orionticket.identity.application.port.out.IdentityEventPublisherPort;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.infrastructure.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqIdentityEventPublisherAdapter implements IdentityEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishStaffCreated(User staff) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "StaffCreated");
        event.put("userId", staff.getUserId());
        event.put("email", staff.getEmail());
        event.put("fullName", staff.getFullName());
        event.put("roleId", staff.getRoleId());
        event.put("organizerId", staff.getOrganizerId());
        
        log.info("Publishing StaffCreated event for user: {}", staff.getEmail());
        
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.IDENTITY_EXCHANGE,
                "identity.staff.created",
                event
        );
    }
}
