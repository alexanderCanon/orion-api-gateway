package com.orionticket.identity.infrastructure.adapters.in.rest;

import com.orionticket.identity.application.port.in.RoleManagementUseCase;
import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.infrastructure.adapters.out.security.AuthenticatedUserResolver;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.CreateRoleRequest;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.RoleResponse;
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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Roles", description = "Role and permission administration endpoints")
public class RoleManagementController {

    private final RoleManagementUseCase roleManagementUseCase;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Operation(summary = "Create role", description = "Creates a platform role with its permissions.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Role created"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        Role role = roleManagementUseCase.createRole(request.getName(), request.getPermissions(), authenticatedUserResolver.currentUser().userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(role));
    }

    @Operation(summary = "List roles", description = "Returns all configured platform roles.")
    @GetMapping
    public ResponseEntity<List<RoleResponse>> getAllRoles() {
        List<RoleResponse> roles = roleManagementUseCase.getAllRoles().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(roles);
    }

    @Operation(summary = "Update role", description = "Updates a role name and permissions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Role not found")
    })
    @PutMapping("/{roleId}")
    public ResponseEntity<RoleResponse> updateRole(
            @PathVariable UUID roleId,
            @Valid @RequestBody CreateRoleRequest request) {
        Role role = roleManagementUseCase.updateRole(roleId, request.getName(), request.getPermissions(), authenticatedUserResolver.currentUser().userId());
        return ResponseEntity.ok(mapToResponse(role));
    }

    @Operation(summary = "Delete role", description = "Deletes a role when allowed by business rules.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Role deleted"),
            @ApiResponse(responseCode = "404", description = "Role not found"),
            @ApiResponse(responseCode = "409", description = "Role cannot be deleted")
    })
    @DeleteMapping("/{roleId}")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID roleId) {
        roleManagementUseCase.deleteRole(roleId, authenticatedUserResolver.currentUser().userId());
        return ResponseEntity.noContent().build();
    }

    private RoleResponse mapToResponse(Role role) {
        return RoleResponse.builder()
                .roleId(role.getRoleId())
                .name(role.getName())
                .permissions(role.getPermissions())
                .build();
    }
}
