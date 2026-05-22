package com.orionticket.identity.infrastructure.adapters.in.rest;

import com.orionticket.identity.application.port.in.LoginUserUseCase;
import com.orionticket.identity.application.port.in.RegisterUserUseCase;
import com.orionticket.identity.domain.model.Role;
import com.orionticket.identity.domain.model.User;
import com.orionticket.identity.domain.port.out.RoleRepositoryPort;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.RegisterRequest;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.RegisterResponse;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.LoginRequest;
import com.orionticket.identity.infrastructure.adapters.in.rest.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Authentication", description = "Buyer registration and login endpoints")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;
    private final RoleRepositoryPort roleRepositoryPort;
    private final long jwtExpirationSeconds;

    public AuthController(
            RegisterUserUseCase registerUserUseCase,
            LoginUserUseCase loginUserUseCase,
            RoleRepositoryPort roleRepositoryPort,
            @Value("${jwt.expiration:${JWT_EXPIRATION:86400}}") long jwtExpirationSeconds) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUserUseCase = loginUserUseCase;
        this.roleRepositoryPort = roleRepositoryPort;
        this.jwtExpirationSeconds = jwtExpirationSeconds;
    }

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

    @Operation(summary = "Login user", description = "Authenticates a user and returns a JWT access token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = loginUserUseCase.login(request.getEmail(), request.getPassword());
        User user = loginUserUseCase.getUserByEmail(request.getEmail());
        Role role = roleRepositoryPort.findById(user.getRoleId())
                .orElseThrow(() -> new IllegalStateException("User role not found: " + user.getRoleId()));

        LoginResponse response = LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationSeconds)
                .userId(user.getUserId())
                .role(role.getName())
                .organizerId(user.getOrganizerId())
                .build();

        return ResponseEntity.ok(response);
    }
}
