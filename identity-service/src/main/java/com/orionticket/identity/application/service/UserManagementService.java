package com.orionticket.identity.application.service;

import com.orionticket.identity.application.port.in.UserManagementUseCase;
import com.orionticket.identity.application.port.out.AuditLogPort;
import com.orionticket.identity.application.port.out.IdentityEventPublisherPort;
import com.orionticket.identity.domain.exception.RoleNotAllowedException;
import com.orionticket.identity.domain.exception.UserAlreadyExistsException;
import com.orionticket.identity.domain.exception.UserNotFoundException;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.model.UserStatus;
import com.orionticket.identity.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserManagementService implements UserManagementUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final AuditLogPort auditLogPort;
    private final IdentityEventPublisherPort eventPublisherPort;

    @Override
    @Transactional
    public User suspendUser(UUID userId, UUID adminId) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));

        user.suspend();

        User savedUser = userRepositoryPort.save(user);
        auditLogPort.logAction(adminId, "SUSPEND_USER", "User " + userId + " was suspended.");

        return savedUser;
    }

    @Override
    @Transactional
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
    @Transactional
    public User createUser(String email, String passwordHash, String fullName, String phone, UUID roleId, UUID organizerId, UUID adminId) {
        if (userRepositoryPort.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("El correo " + email + " ya está registrado.");
        }

        User newUser = User.builder()
                .userId(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordHash)
                .fullName(fullName)
                .phone(phone)
                .status(UserStatus.ACTIVE.name())
                .roleId(roleId)
                .organizerId(organizerId)
                .createdAt(java.time.ZonedDateTime.now())
                .build();

        try {
            User savedUser = userRepositoryPort.save(newUser);
            auditLogPort.logAction(adminId, "CREATE_USER", "User " + savedUser.getUserId() + " created by admin");
            return savedUser;
        } catch (DataIntegrityViolationException ex) {
            // Cierra la ventana del check-then-act bajo concurrencia.
            throw new UserAlreadyExistsException("El correo " + email + " ya está registrado.");
        }
    }

    @Override
    @Transactional
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
    @Transactional
    public User createOrganizerStaff(UUID organizerId, String email, String passwordHash, String fullName, String phone, UUID roleId, UUID creatorId) {
        // Validación de roles permitidos para staff (US-009)
        String venueStaffId = "00000000-0000-0000-0000-000000000004";
        String doorValidatorId = "00000000-0000-0000-0000-000000000005";

        if (!roleId.toString().equals(venueStaffId) && !roleId.toString().equals(doorValidatorId)) {
            throw new RoleNotAllowedException("Rol no permitido para staff de organizador. Solo se permite VENUE_STAFF o DOOR_VALIDATOR.");
        }

        if (userRepositoryPort.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("El correo " + email + " ya está registrado.");
        }

        User newUser = User.builder()
                .userId(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordHash)
                .fullName(fullName)
                .phone(phone)
                .status(UserStatus.ACTIVE.name())
                .roleId(roleId)
                .organizerId(organizerId)
                .createdAt(java.time.ZonedDateTime.now())
                .build();

        try {
            User savedUser = userRepositoryPort.save(newUser);
            auditLogPort.logAction(creatorId, "CREATE_STAFF", "Staff " + savedUser.getUserId() + " created for organizer " + organizerId);

            eventPublisherPort.publishStaffCreated(savedUser);

            return savedUser;
        } catch (DataIntegrityViolationException ex) {
            throw new UserAlreadyExistsException("El correo " + email + " ya está registrado.");
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Aplica las transiciones válidas del dominio definidas en {@link User}:
     * {@code ACTIVE} y {@code UNVERIFIED} son alcanzables desde cualquier estado
     * no suspendido; {@code SUSPENDED} es alcanzable desde cualquier estado.
     * Reactivar una cuenta suspendida requiere pasar explícitamente por
     * {@code ACTIVE} o {@code UNVERIFIED}.</p>
     */
    @Override
    @Transactional
    public User updateUserStatus(UUID userId, UserStatus newStatus, UUID adminId) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));

        switch (newStatus) {
            case ACTIVE -> user.activate();
            case UNVERIFIED -> {
                if (user.isSuspended()) {
                    throw new IllegalStateException(
                            "Cannot set a suspended account back to UNVERIFIED; reactivate first.");
                }
                user.setStatus(UserStatus.UNVERIFIED.name());
            }
            case SUSPENDED -> user.suspend();
        }

        User savedUser = userRepositoryPort.save(user);
        auditLogPort.logAction(adminId, "UPDATE_USER_STATUS",
                "User " + userId + " status updated to " + newStatus);
        return savedUser;
    }
}
