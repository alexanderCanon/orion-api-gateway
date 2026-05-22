package com.orionticket.identity.application.port.in;

import com.orionticket.identity.domain.model.User;
import java.util.UUID;

public interface UserManagementUseCase {
    User suspendUser(UUID userId, UUID adminId);
    User updateUserRole(UUID userId, UUID newRoleId, UUID adminId);
    java.util.List<User> getAllUsers();
    User createUser(String email, String passwordHash, String fullName, String phone, UUID roleId, UUID organizerId, UUID adminId);
    User updateUser(UUID userId, String fullName, String phone, UUID adminId);
    User createOrganizerStaff(UUID organizerId, String email, String passwordHash, String fullName, String phone, UUID roleId, UUID creatorId);
}
