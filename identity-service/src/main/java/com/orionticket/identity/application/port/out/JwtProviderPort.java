package com.orionticket.identity.application.port.out;

import com.orionticket.identity.domain.model.User;

public interface JwtProviderPort {
    String generateToken(User user);
}
