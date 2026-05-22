package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.UserManagementUseCase;
import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.IdentityEventPublisherPort;
import com.orionticket.identity.domain.exception.UserNotFoundException;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserManagementService implements UserManagementUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final AuditLogPort auditLogPort;
    private final IdentityEventPublisherPort eventPublisherPort;

    @Override
    public User suspendUser(UUID userId, UUID adminId) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));

        user.suspend();
        
        User savedUser = userRepositoryPort.save(user);
        auditLogPort.logAction(adminId, "SUSPEND_USER", "User " + userId + " was suspended.");
        
        return savedUser;
    }

    @Override
    public User updateUserRole(UUID userId, UUID newRoleId, UUID adminId) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));

        user.setRoleId(newRoleId);
        
        User savedUser = userRepositoryPort.save(user);
        auditLogPort.logAction(adminId, "UPDATE_USER_ROLE", "User " + userId + " role updated to " + newRoleId);
        
        return savedUser;
    }

    @Override
    public java.util.List<User> getAllUsers() {
        return userRepositoryPort.findAll();
    }

    @Override
    public User createUser(String email, String passwordHash, String fullName, String phone, UUID roleId, UUID organizerId, UUID adminId) {
        if (userRepositoryPort.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists");
        }
        
        User newUser = User.builder()
                .userId(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordHash)
                .fullName(fullName)
                .phone(phone)
                .status("ACTIVE")
                .roleId(roleId)
                .organizerId(organizerId)
                .createdAt(java.time.ZonedDateTime.now())
                .build();
                
        User savedUser = userRepositoryPort.save(newUser);
        auditLogPort.logAction(adminId, "CREATE_USER", "User " + savedUser.getUserId() + " created by admin");
        return savedUser;
    }

    @Override
    public User updateUser(UUID userId, String fullName, String phone, UUID adminId) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));
                
        user.setFullName(fullName);
        user.setPhone(phone);
        
        User savedUser = userRepositoryPort.save(user);
        auditLogPort.logAction(adminId, "UPDATE_USER", "User " + userId + " details updated");
        return savedUser;
    }

    @Override
    public User createOrganizerStaff(UUID organizerId, String email, String passwordHash, String fullName, String phone, UUID roleId, UUID creatorId) {
        // Validación de roles permitidos para staff (US-009)
        String venueStaffId = "00000000-0000-0000-0000-000000000004";
        String doorValidatorId = "00000000-0000-0000-0000-000000000005";
        
        if (!roleId.toString().equals(venueStaffId) && !roleId.toString().equals(doorValidatorId)) {
            throw new com.orionticket.identity.domain.exception.RoleNotAllowedException("Rol no permitido para staff de organizador. Solo se permite VENUE_STAFF o DOOR_VALIDATOR.");
        }

        if (userRepositoryPort.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        User newUser = User.builder()
                .userId(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordHash)
                .fullName(fullName)
                .phone(phone)
                .status("ACTIVE")
                .roleId(roleId)
                .organizerId(organizerId)
                .createdAt(java.time.ZonedDateTime.now())
                .build();

        User savedUser = userRepositoryPort.save(newUser);
        auditLogPort.logAction(creatorId, "CREATE_STAFF", "Staff " + savedUser.getUserId() + " created for organizer " + organizerId);
        
        eventPublisherPort.publishStaffCreated(savedUser);
        
        return savedUser;
    }
}
