package com.orionticket.identity.application.port.in;

import com.orionticket.identity.domain.model.User;

public interface LoginUserUseCase {
    String login(String email, String rawPassword);
    User getUserByEmail(String email);
}
