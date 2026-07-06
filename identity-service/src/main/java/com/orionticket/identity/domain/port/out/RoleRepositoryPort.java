package com.orionticket.identity.domain.port.out;

import com.orionticket.identity.domain.model.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepositoryPort {
    Role save(Role role);
    Optional<Role> findById(UUID roleId);
    Optional<Role> findByName(String name);
    List<Role> findAll();
    void deleteById(UUID roleId);
}
