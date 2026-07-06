package com.orionticket.identity.application.port.in;

import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.model.UserStatus;
import java.util.UUID;

public interface UserManagementUseCase {
    User suspendUser(UUID userId, UUID adminId);
    User updateUserRole(UUID userId, UUID newRoleId, UUID adminId);
    java.util.List<User> getAllUsers();
    User createUser(String email, String passwordHash, String fullName, String phone, UUID roleId, UUID organizerId, UUID adminId);
    User updateUser(UUID userId, String fullName, String phone, UUID adminId);
    User createOrganizerStaff(UUID organizerId, String email, String passwordHash, String fullName, String phone, UUID roleId, UUID creatorId);

    /**
     * Actualiza el estado de un usuario aplicando las reglas de transición
     * del dominio y persistiendo el cambio.
     *
     * @param userId   identificador del usuario objetivo
     * @param newStatus nuevo estado (debe ser un valor válido de {@link UserStatus})
     * @param adminId  identificador del administrador que ejecuta la acción
     * @return el usuario actualizado y persistido
     */
    User updateUserStatus(UUID userId, UserStatus newStatus, UUID adminId);
}
