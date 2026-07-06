package com.orionticket.identity.infrastructure.adapters.in.rest;

import com.orionticket.identity.application.port.in.UserManagementUseCase;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.infrastructure.adapters.out.security.AuthenticatedUserResolver;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.UpdateRoleRequest;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.UpdateUserRequest;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.CreateUserRequest;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.UpdateStatusRequest;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userManagementUseCase.createUser(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName(),
                request.getPhone(),
                request.getRoleId(),
                request.getOrganizerId(),
                authenticatedUserResolver.currentUser().userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(user));
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
            @Valid @RequestBody UpdateUserRequest request) {
        User updatedUser = userManagementUseCase.updateUser(userId, request.getFullName(), request.getPhone(), authenticatedUserResolver.currentUser().userId());
        return ResponseEntity.ok(mapToResponse(updatedUser));
    }

    @Operation(summary = "Update user status", description = "Updates the status of a user account (ACTIVE, SUSPENDED, UNVERIFIED) with domain-validated transitions and real persistence.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Invalid status or transition"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PatchMapping("/{userId}/status")
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateStatusRequest request) {
        User updatedUser = userManagementUseCase.updateUserStatus(
                userId,
                request.getStatus(),
                authenticatedUserResolver.currentUser().userId());
        return ResponseEntity.ok(mapToResponse(updatedUser));
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
