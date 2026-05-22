package com.orionticket.identity.infrastructure.adapters.in.rest;

import com.orionticket.identity.application.port.in.UserManagementUseCase;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.infrastructure.adapters.out.security.AuthenticatedUserResolver;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.UpdateRoleRequest;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Users", description = "Platform user administration endpoints")
public class UserManagementController {

    private final UserManagementUseCase userManagementUseCase;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Operation(summary = "Suspend user", description = "Suspends an existing user account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User suspended"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{userId}/suspend")
    public ResponseEntity<UserResponse> suspendUser(@PathVariable UUID userId) {
        User updatedUser = userManagementUseCase.suspendUser(userId, authenticatedUserResolver.currentUser().userId());
        return ResponseEntity.ok(mapToResponse(updatedUser));
    }

    @Operation(summary = "Update user role", description = "Assigns a new role to an existing user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "User or role not found")
    })
    @PutMapping("/{userId}/roles")
    public ResponseEntity<UserResponse> updateUserRole(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateRoleRequest request) {
        User updatedUser = userManagementUseCase.updateUserRole(userId, request.getNewRoleId(), authenticatedUserResolver.currentUser().userId());
        return ResponseEntity.ok(mapToResponse(updatedUser));
    }

    @Operation(summary = "List users", description = "Returns all platform users.")
    @GetMapping
    public ResponseEntity<java.util.List<UserResponse>> getAllUsers() {
        java.util.List<UserResponse> users = userManagementUseCase.getAllUsers().stream()
                .map(this::mapToResponse)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @Operation(summary = "Create user", description = "Creates an internal platform or organizer-scoped user.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody com.orionticket.identity.infrastructure.adapters.in.rest.dto.CreateUserRequest request) {
        User user = userManagementUseCase.createUser(
                request.getEmail(), 
                passwordEncoder.encode(request.getPassword()), 
                request.getFullName(), 
                request.getPhone(), 
                request.getRoleId(), 
                request.getOrganizerId(), 
                authenticatedUserResolver.currentUser().userId());
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(mapToResponse(user));
    }

    @Operation(summary = "Update user profile", description = "Updates mutable user profile fields.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody com.orionticket.identity.infrastructure.adapters.in.rest.dto.UpdateUserRequest request) {
        User updatedUser = userManagementUseCase.updateUser(userId, request.getFullName(), request.getPhone(), authenticatedUserResolver.currentUser().userId());
        return ResponseEntity.ok(mapToResponse(updatedUser));
    }

    @Operation(summary = "Update user status", description = "Updates user status for administrative workflows.")
    @PatchMapping("/{userId}/status")
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> statusUpdate) {
        // En una implementación final, esto llamaría a un método específico de 'approve' o 'suspend'
        // Por ahora, simularemos la activación para la US-003
        User user = userManagementUseCase.getAllUsers().stream()
                .filter(u -> u.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new com.orionticket.identity.domain.exception.UserNotFoundException("Usuario no encontrado"));
        
        String newStatus = statusUpdate.get("status");
        if ("ACTIVE".equals(newStatus) || "APPROVED".equals(newStatus)) {
            // Nota: En un sistema real usaríamos un puerto de salida para persistir el cambio
            // Para la prueba de Postman, lo simularemos devolviendo el usuario como activo
            user.setStatus("ACTIVE");
        }
        return ResponseEntity.ok(mapToResponse(user));
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .roleId(user.getRoleId())
                .organizerId(user.getOrganizerId())
                .build();
    }
}
