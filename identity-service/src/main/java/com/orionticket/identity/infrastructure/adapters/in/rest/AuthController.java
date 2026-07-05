package com.orionticket.identity.infrastructure.adapters.in.rest;

import com.orionticket.identity.application.port.in.AuthResult;
import com.orionticket.identity.application.port.in.LoginUserUseCase;
import com.orionticket.identity.application.port.in.LogoutUseCase;
import com.orionticket.identity.application.port.in.RefreshTokenUseCase;
import com.orionticket.identity.application.port.in.RegisterUserUseCase;
import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.LoginRequest;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.LoginResponse;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.LogoutRequest;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.RefreshRequest;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.RegisterRequest;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.RegisterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Buyer registration, login, refresh and logout endpoints")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RoleRepositoryPort roleRepositoryPort;

    @Operation(summary = "Register buyer", description = "Registers a buyer account in UNVERIFIED status.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Buyer registered"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {

        User user = registerUserUseCase.registerBuyer(
                request.getEmail(),
                request.getPassword(),
                request.getFullName(),
                request.getPhone()
        );

        RegisterResponse response = RegisterResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .status(user.getStatus())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Login user", description = "Authenticates a user and returns access + refresh tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "403", description = "Account disabled")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = resolveClientIp(httpRequest);

        AuthResult result = loginUserUseCase.login(request.getEmail(), request.getPassword(), userAgent, ipAddress);

        return ResponseEntity.ok(toLoginResponse(result));
    }

    @Operation(summary = "Refresh token", description = "Rotates a refresh token and returns a new access + refresh pair. Reuse of an already-rotated token revokes the entire chain.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refresh successful"),
            @ApiResponse(responseCode = "401", description = "Invalid, expired or revoked refresh token"),
            @ApiResponse(responseCode = "403", description = "Account disabled")
    })
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                                 HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = resolveClientIp(httpRequest);

        AuthResult result = refreshTokenUseCase.refresh(request.getRefreshToken(), userAgent, ipAddress);

        return ResponseEntity.ok(toLoginResponse(result));
    }

    @Operation(summary = "Logout", description = "Revokes the presented refresh token. With {all: true} revokes all sessions of the user.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logout successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        logoutUseCase.logout(request.getRefreshToken(), request.isAll());
        return ResponseEntity.noContent().build();
    }

    private LoginResponse toLoginResponse(AuthResult result) {
        User user = result.user();
        Role role = roleRepositoryPort.findById(user.getRoleId())
                .orElseThrow(() -> new IllegalStateException("User role not found: " + user.getRoleId()));

        return LoginResponse.builder()
                .accessToken(result.accessToken())
                .refreshToken(result.refreshToken())
                .tokenType(result.tokenType())
                .expiresIn(result.expiresIn())
                .userId(user.getUserId())
                .role(role.getName())
                .organizerId(user.getOrganizerId())
                .build();
    }

    /**
     * Resuelve la IP del cliente respetando X-Forwarded-For (el servicio
     * tiene forward-headers-strategy: native, pero este endpoint es público
     * y conviene ser explícito para el audit/refresh).
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}
