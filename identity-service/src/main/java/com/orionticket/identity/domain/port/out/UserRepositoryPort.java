package com.orionticket.identity.domain.port.out;

import com.orionticket.identity.domain.model.User;
import java.util.Optional;

public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findByEmail(String email);
    Optional<User> findById(java.util.UUID userId);
    java.util.List<User> findAll();
}
