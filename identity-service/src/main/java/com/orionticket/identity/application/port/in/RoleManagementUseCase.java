package com.orionticket.identity.application.port.in;

import com.orionticket.identity.domain.model.Role;

import java.util.List;
import java.util.UUID;

public interface RoleManagementUseCase {
    Role createRole(String name, List<String> permissions, UUID adminId);
    Role updateRole(UUID roleId, String name, List<String> permissions, UUID adminId);
    void deleteRole(UUID roleId, UUID adminId);
    List<Role> getAllRoles();
}
