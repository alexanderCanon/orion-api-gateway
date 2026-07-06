package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.RoleManagementUseCase;
import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.domain.exception.RoleNotFoundException;
import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleManagementService implements RoleManagementUseCase {

    private final RoleRepositoryPort roleRepositoryPort;
    private final AuditLogPort auditLogPort;

    @Override
    @Transactional
    public Role createRole(String name, List<String> permissions, UUID adminId) {
        Role newRole = Role.builder()
                .roleId(UUID.randomUUID())
                .name(name)
                .permissions(permissions)
                .build();

        try {
            Role savedRole = roleRepositoryPort.save(newRole);
            auditLogPort.logAction(adminId, "CREATE_ROLE",
                    "Role " + savedRole.getRoleId() + " created with name " + name);
            return savedRole;
        } catch (DataIntegrityViolationException ex) {
            // El nombre del rol es único; dos requests concurrentes con el
            // mismo nombre pueden pasar el check-then-act y explotar aquí.
            throw new DataIntegrityViolationException(
                    "El rol con nombre '" + name + "' ya existe.", ex);
        }
    }

    @Override
    @Transactional
    public Role updateRole(UUID roleId, String name, List<String> permissions, UUID adminId) {
        Role role = roleRepositoryPort.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Rol no encontrado: " + roleId));

        role.setName(name);
        role.setPermissions(permissions);

        Role savedRole = roleRepositoryPort.save(role);
        auditLogPort.logAction(adminId, "UPDATE_ROLE", "Role " + roleId + " updated");
        return savedRole;
    }

    @Override
    @Transactional
    public void deleteRole(UUID roleId, UUID adminId) {
        roleRepositoryPort.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Rol no encontrado: " + roleId));

        roleRepositoryPort.deleteById(roleId);
        auditLogPort.logAction(adminId, "DELETE_ROLE", "Role " + roleId + " deleted");
    }

    @Override
    public List<Role> getAllRoles() {
        return roleRepositoryPort.findAll();
    }
}
