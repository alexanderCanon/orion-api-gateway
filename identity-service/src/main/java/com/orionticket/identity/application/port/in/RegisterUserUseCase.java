package com.orionticket.identity.application.port.in;

import com.orionticket.identity.domain.model.User;

public interface RegisterUserUseCase {
    User registerBuyer(String email, String rawPassword, String fullName, String phone);
}
