# Identity Service - OrionTicket

This is the authentication, authorization, and identity management microservice for the **OrionTicket** platform. This document is designed to help developers and AI tools quickly understand the scope, architecture, and limitations of the service without needing to explore the entire codebase.

---

## 1. Service Purpose
The `identity-service` acts as the central Identity Provider (IdP) and Authentication Server for the platform. It is responsible for:
* Registering buyers (`BUYER`).
* Authenticating users via passwords and issuing digitally signed **JWT** access tokens using **RS256**.
* Exposing the public key for token signature verification through a standard **JWKS** endpoint (`/.well-known/jwks.json`), enabling the API Gateway to validate tokens in a decentralized manner.
* Providing role-based access control (RBAC) permissions for the API Gateway and downstream microservices.
* Registering internal staff (`VENUE_STAFF`, `DOOR_VALIDATOR`) associated with an event organizer and publishing related events to RabbitMQ.

---

## 2. Architecture (Hexagonal / Ports & Adapters)
The project is structured following the **Hexagonal Architecture** pattern, splitting the business logic into three main layers inside `src/main/java/com/orionticket/identity/`:

```
identity/
├── domain/                  # Core business logic (framework-free)
│   ├── model/               # Domain entities (User, Role)
│   ├── port/out/            # Persistence interfaces (UserRepositoryPort, etc.)
│   └── exception/           # Business exceptions (UserNotFoundException, etc.)
├── application/             # Use cases and orchestration rules
│   ├── port/in/             # Inbound Ports / Use case interfaces (LoginUserUseCase, etc.)
│   ├── port/out/            # Outbound Ports / Infrastructure interfaces (JwtProviderPort, etc.)
│   └── service/             # Use case implementations (LoginUserService, etc.)
└── infrastructure/          # Technical details and concrete adapters
    ├── adapters/            # Inbound and Outbound adapters
    │   ├── in/rest/         # REST Controllers and DTOs
    │   └── out/             # JPA Persistence, Security (JWT, BCrypt), Messaging (RabbitMQ), Audit logs
    └── config/              # Spring configuration classes (Security, OpenApi, RabbitMQ)
```

---

## 3. Technology Stack
* **Language & Framework**: Java 21, Spring Boot 3.3.0.
* **Security**: Spring Security 6 & Spring OAuth2 Resource Server.
* **Persistence**: PostgreSQL with Spring Data JPA.
* **Database Migrations**: Flyway (schema: `identity`).
* **Messaging**: RabbitMQ (Spring Boot AMQP).
* **Token Operations**: JJWT (Java JWT) for token generation.
* **Metrics & Monitoring**: Spring Boot Actuator & Micrometer Prometheus.
* **API Documentation**: Springdoc OpenAPI (Swagger UI).

---

## 4. Security & Control Mechanisms
1. **Passwords**: Hashed and validated using **BCrypt** (`BCryptPasswordEncoder`).
2. **JWT Access Tokens**: Digitally signed using **RS256** (asymmetric RSA with a private/public key pair).
   * **JWT Claims**: Includes `sub` (userId), `email`, `role`, `permissions` (list of permission strings), and `organizerId` (optional).
3. **Decentralized Verification (JWKS)**:
   * The service exposes its public key at `GET /.well-known/jwks.json`.
   * Downstream services (such as the API Gateway) fetch and cache this public key to validate JWT signatures reactively without querying the identity service on every request.

---

## 5. Role-Based Access Control (RBAC)
SQL migrations (`db/migration/`) seed and map permissions to the main roles within the `roles`, `permissions`, and `users` tables:

| Role | Key in Database & JWT | Primary Permissions |
| :--- | :--- | :--- |
| **Buyer** | `BUYER` | `orders:create`, `orders:read:self`, `tickets:read:self`, `reservations:create` |
| **Organizer** | `ORGANIZER` | `events:create`, `events:update:own`, `venues:create`, `staff:create:own`, `reports:read:own` |
| **Super Admin** | `SUPER_ADMIN` | `*` (Full access) |
| **Venue Staff** | `VENUE_STAFF` | `validators:read:own`, `validations:read:own` |
| **Door Validator** | `DOOR_VALIDATOR` | `validations:create`, `tickets:lookup` |
| **Technical Support** | `SUPPORT` | `users:read`, `orders:read`, `tickets:resend` |
| **Finance** | `FINANCE` | `payments:read`, `reports:read:financial` |
| **Marketing** | `MARKETING` | `analytics:read`, `promotions:manage` |
| **Platform Operator** | `PLATFORM_OPERATOR` | `events:approve`, `events:reject`, `reports:read:platform` |

---

## 6. REST API Endpoints

### Public Endpoints (No Authentication Required)
* `POST /v1/auth/register`: Registers a new buyer (`BUYER`). The user is created with an initial state of `UNVERIFIED`.
* `POST /v1/auth/login`: Authenticates the user via email and password, returning a JWT Access Token.
* `GET /.well-known/jwks.json`: Retrieves the JWK public key set.
* `GET /actuator/health`: System health information.
* `GET /swagger-ui.html`: Interactive API documentation.

### Private Endpoints (Valid JWT Bearer Token Required)
* `POST /v1/organizers/{organizerId}/staff`: Registers new staff for an organizer.
  * **Access**: `ORGANIZER` (restricted to their own `organizerId`) or `SUPER_ADMIN`.
  * **Constraint**: Only allows assigning the `VENUE_STAFF` or `DOOR_VALIDATOR` roles.
* `PUT /v1/users/{userId}/suspend`: Suspends an active user.
  * **Access**: `SUPER_ADMIN`.
* `PUT /v1/users/{userId}/roles`: Reassigns a user's role.
  * **Access**: `SUPER_ADMIN`.
* `GET /v1/users`: Lists all users on the platform.
  * **Access**: `SUPER_ADMIN`.
* `POST /v1/users`: Creates a platform administrator, operator, or staff user.
  * **Access**: `SUPER_ADMIN`.

---

## 7. Event-Driven Integration (RabbitMQ)
The microservice publishes an asynchronous event whenever a new staff member is successfully created for an organizer:
* **Exchange**: `identity.exchange` (Topic type)
* **Routing Key**: `identity.staff.created`
* **Event Payload**:
  ```json
  {
    "eventType": "StaffCreated",
    "userId": "UUID-of-the-staff",
    "email": "staff@example.com",
    "fullName": "Staff Full Name",
    "roleId": "UUID-of-the-assigned-role",
    "organizerId": "UUID-of-the-organizer"
  }
  ```

---

## 8. Current Limitations & Pending Production Tasks
This microservice is **not yet production-ready** due to the following MVP gaps:
1. **Mock Implementation in Status Update**: The `PATCH /v1/users/{userId}/status` endpoint simulates user activation. It alters the state in the returned in-memory object but **does not persist changes to the database**.
2. **Missing Account Verification Flow**: Buyers register in `UNVERIFIED` status, but no verification mechanism (such as sending or verifying an activation code/link via email) has been implemented to promote them to `ACTIVE`.
3. **No JWT Revocation (Blocklisting)**: JWT validation is stateless. If a user is suspended, previously issued JWTs remain valid until they expire, as there is no blocklist or token validation callback in the Gateway.
4. **No Refresh Tokens**: The default Access Token expiration time is set to 24 hours (`86400` s) to compensate for the lack of a Refresh Token mechanism. This exposes a security risk if the token is compromised.
5. **No Rate Limiting**: The `/v1/auth/login` endpoint does not restrict login attempts, making it vulnerable to brute-force and credential stuffing attacks.
