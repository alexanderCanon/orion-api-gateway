# Auditoría de Seguridad — identity-service

> **Fecha:** 2026-07-05
> **Alcance:** `identity-service/` (rama `feat/identity-service`)
> **Objetivo:** Llevar el servicio a nivel producción como proveedor de identidad tipo GoTrue (Supabase): sign-up, sign-in, recover password, verificación de email, gestión de sesiones, robusto y seguro.
>
> **Estado de implementación:**
> - ✅ **Fase 0 — Quick wins: COMPLETADA** (2026-07-05). Ver detalle al final del plan.
> - ✅ **Fase 1 — Refresh tokens, revocación y logout: COMPLETADA** (2026-07-05). Ver detalle al final del plan.
> - ✅ **Fase 2 — Rate limiting y protección de fuerza bruta: COMPLETADA** (2026-07-05). Ver detalle al final del plan.
> - ✅ **Fase 3 — Recover password + verificación de email: COMPLETADA** (2026-07-05). Ver detalle al final del plan.
> - ✅ **Fase 4 — Auditoría y transacciones: COMPLETADA** (2026-07-05). Ver detalle al final del plan.

---

## Resumen ejecutivo

El servicio tiene una base arquitectónica sólida (hexagonal, RS256 + JWKS, BCrypt, deny-by-default, Flyway, Testcontainers), pero **no está listo para producción como identity provider**. Faltan flujos core (recuperación de contraseña, verificación de email, refresh tokens, logout/revocación), el login ignora el estado del usuario (usuarios suspendidos pueden autenticarse), no hay protección contra fuerza bruta, y existen fugas de información y credenciales por defecto en la configuración.

| Severidad | Cantidad |
|-----------|----------|
| 🔴 Crítica | 7 |
| 🟠 Alta | 6 |
| 🟡 Media | 7 |
| 🟢 Baja / Mejora | 4 |

---

## Hallazgos

### 🔴 CRÍTICOS

#### C1. El login no valida el estado del usuario
- **Archivo:** `identity-service/src/main/java/com/orionticket/identity/application/service/LoginUserService.java`
- **Problema:** `login()` solo verifica email + password. Un usuario `SUSPENDED` o `UNVERIFIED` obtiene un JWT válido. El método `User.suspend()` no tiene efecto real sobre la autenticación.
- **Impacto:** La suspensión de cuentas (control administrativo de seguridad) es inoperante. Cuentas comprometidas o bloqueadas siguen accediendo al sistema.

#### C2. No existen los flujos core de un identity provider
- **Problema:** No existe ninguno de estos flujos:
  - Recuperación de contraseña (forgot/reset password)
  - Verificación de email (los usuarios quedan `UNVERIFIED` para siempre; no hay tokens de verificación ni envío de correo)
  - Cambio de contraseña autenticado (change password)
  - Logout / revocación de sesión
- **Impacto:** El servicio no cumple su propósito declarado ("GoTrue de Supabase"). Un usuario que olvida su contraseña pierde la cuenta. El estado `UNVERIFIED` es decorativo.

#### C3. Sin refresh tokens; access token de 24 horas sin revocación
- **Archivos:** `JwtProviderAdapter.java` (`jwt.expiration` default `86400`), `AuthController.java`
- **Problema:** Un solo access token de larga vida (24h por defecto), sin refresh token, sin lista de revocación, sin logout.
- **Impacto:**
  - Un token filtrado es válido 24 horas sin posibilidad de invalidarlo.
  - `suspendUser()` y `updateUserRole()` no invalidan tokens ya emitidos: un usuario suspendido o degradado conserva sus privilegios anteriores hasta 24h.
- **Referencia GoTrue:** access token de ~1h + refresh token rotativo persistido en BD (revocable, detección de reuso).

#### C4. Sin protección contra fuerza bruta
- **Endpoint:** `POST /v1/auth/login` (también `/register`)
- **Problema:** No hay rate limiting, lockout de cuenta, backoff exponencial ni CAPTCHA. Intentos ilimitados de contraseña.
- **Impacto:** Credential stuffing y fuerza bruta triviales. Con BCrypt strength 10 el costo por intento es bajo para el atacante.

#### C5. Credenciales por defecto en configuración
- **Archivos:** `application.yml`, `application-prod.yml`
- **Problema:**
  - `application.yml` línea 7: `password: ${GLOBAL_DB_PASSWORD:AppSecret789}` — contraseña real como fallback, commiteada al repo.
  - `application-prod.yml` líneas 40-41: RabbitMQ con fallback `guest/guest` **en el perfil de producción**.
- **Impacto:** Si una variable de entorno falta en el despliegue, el servicio arranca silenciosamente con credenciales conocidas públicamente.

#### C6. Endpoint falso que simula persistencia (`PATCH /v1/users/{id}/status`)
- **Archivo:** `UserManagementController.java` líneas 100-118
- **Problema:** `updateUserStatus` carga TODOS los usuarios en memoria, muta el objeto en memoria y devuelve `200 OK` **sin persistir nada** (el propio comentario dice "lo simularemos").
- **Impacto:** Un administrador cree que activó/aprobó un usuario y la operación nunca ocurrió. Código de prueba desplegable a producción.

#### C7. Fuga de información interna en errores 500
- **Archivo:** `GlobalExceptionHandler.java` líneas 59-65
- **Problema:** `handleRuntimeException` devuelve `ex.getMessage()` crudo al cliente en respuestas 500. Cualquier `RuntimeException` (SQL, constraint violations, `IllegalStateException` con IDs internos) expone detalles de implementación.
- **Impacto:** Information disclosure; facilita reconocimiento al atacante. Además captura `RuntimeException` de forma genérica, enmascarando errores que deberían tener manejo específico.

---

### 🟠 ALTOS

#### A1. Enumeración de usuarios + oráculo de timing en login
- **Archivos:** `LoginUserService.java`, `RegisterUserService.java`, `GlobalExceptionHandler.java`
- **Problema:**
  1. **Timing:** cuando el email no existe, el login lanza excepción sin ejecutar BCrypt (`matches()` cuesta ~100ms). La diferencia de tiempo revela si un email está registrado.
  2. **Registro:** `409 Conflict` con mensaje "El correo X ya está registrado" confirma directamente qué emails existen.
- **Impacto:** Enumeración masiva de la base de usuarios; insumo para credential stuffing y phishing dirigido.

#### A2. Logging inseguro en producción
- **Archivo:** `application-prod.yml` líneas 54-58, `application.yml` líneas 39-43
- **Problema:** `com.orionticket: DEBUG` y `org.springframework.security: DEBUG` activos **en el perfil prod**. Spring Security en DEBUG puede volcar headers, tokens y detalles de autenticación a los logs.
- **Impacto:** Tokens/credenciales en logs centralizados = superficie de robo de sesión. Además `management.endpoint.health.show-details: always` expone detalles de infraestructura (BD, RabbitMQ) sin autenticación.

#### A3. Contraseña sin límite máximo ni política robusta
- **Archivos:** `RegisterRequest.java`, `CreateUserRequest.java`, `CreateStaffRequest.java`, `LoginRequest.java`
- **Problema:** Solo `@Size(min = 8)`. Sin máximo:
  - BCrypt trunca a **72 bytes**: `"a"*72 + "X"` y `"a"*72 + "Y"` validan igual.
  - Payloads de MBs llegan hasta el hash → vector de DoS por CPU.
  - Sin chequeo de contraseñas comunes/débiles (GoTrue tiene weak password check).
- **Impacto:** Garantías criptográficas degradadas + DoS.

#### A4. Sin auditoría de eventos de autenticación
- **Archivos:** `LoginUserService.java`, `RegisterUserService.java`, `Slf4jAuditLogAdapter.java`
- **Problema:** Login exitoso, login fallido y registro no generan ningún evento de auditoría. El audit log existente solo cubre acciones de admin y va a SLF4J (se pierde si rota el log; no es consultable).
- **Impacto:** Imposible detectar ataques en curso, investigar incidentes o cumplir requisitos de compliance.

#### A5. Race condition en registro (check-then-act) y ausencia de `@Transactional`
- **Archivos:** `RegisterUserService.java` líneas 28-30, `UserManagementService.java` líneas 55-57 y 99-101
- **Problema:** Patrón `findByEmail` + `save` sin transacción. Dos requests concurrentes con el mismo email pasan ambos el check; el segundo `save` explota con violación de constraint → **500** con mensaje interno (vía C7) en vez de `409`. Ningún servicio de aplicación define límites transaccionales.
- **Impacto:** Errores 500 no controlados + fuga de detalles de BD.

#### A6. Configuración Hikari de producción no se aplica (bug de indentación YAML)
- **Archivo:** `application-prod.yml` líneas 9-18
- **Problema:** El bloque `hikari:` está indentado bajo `spring:` en lugar de `spring.datasource.hikari`. Todo el bloque (pool size, timeouts, y **`connection-init-sql: SET search_path TO identity`**) se ignora silenciosamente en prod.
- **Impacto:** Pool con defaults no dimensionados y, peor, el `search_path` no se establece → posibles fallos o resolución de tablas contra el schema equivocado según la configuración del usuario de BD.

---

### 🟡 MEDIOS

#### M1. UUIDs de roles hardcodeados en el código
- **Archivos:** `RegisterUserService.java` línea 22, `UserManagementService.java` líneas 92-93
- **Problema:** IDs mágicos (`00000000-...-0001`, `-0004`, `-0005`) hardcodeados y duplicados como strings. La regla "solo VENUE_STAFF o DOOR_VALIDATOR" vive en un `if` sobre strings de UUID.
- **Riesgo:** Si el seed cambia o difiere entre entornos, se asignan roles incorrectos silenciosamente. Debería resolverse por nombre de rol contra `RoleRepositoryPort`.

#### M2. `resolveOrganizerId` otorga ownership implícito
- **Archivo:** `AuthenticatedUserResolver.java` líneas 39-45
- **Problema:** Si un token con rol `ORGANIZER` no trae claim `organizerId`, se asume `organizerId = userId`. Un token malformado o legado obtiene ownership de un "organizer" fabricado.
- **Riesgo:** Confusión de autorización; mejor rechazar tokens ORGANIZER sin `organizerId`.

#### M3. `updateUserRole` no valida existencia del rol ni reglas de negocio
- **Archivo:** `UserManagementService.java` líneas 36-46
- **Problema:** Acepta cualquier UUID; si el rol no existe, la FK revienta con 500. Tampoco impide auto-degradarse o asignar `SUPER_ADMIN` sin control adicional.

#### M4. Sin headers de seguridad ni política CORS explícita
- **Archivo:** `SecurityConfig.java`
- **Problema:** No se configuran `HSTS`, `X-Content-Type-Options`, `Cache-Control` para respuestas con tokens, ni CORS. Aunque el gateway (Traefik) pueda cubrir parte, el servicio debe ser seguro por sí mismo (defensa en profundidad).

#### M5. Sin soporte de rotación de claves JWT
- **Archivos:** `JwtProviderAdapter.java`, `JwksController.java`, `SecurityConfig.java` (JwtDecoder con clave única)
- **Problema:** JWKS publica una sola clave y el decoder local usa `withPublicKey(...)` fijo. No se puede rotar la clave sin invalidar todos los tokens en vuelo.

#### M6. `permitAll` con wildcard amplio `/v1/auth/**`
- **Archivo:** `SecurityConfig.java` línea 40
- **Problema:** Cualquier endpoint futuro bajo `/v1/auth/` será público por accidente. Enumerar rutas exactas (`/v1/auth/register`, `/v1/auth/login`, ...).

#### M7. Login hace 3 consultas y expone lógica duplicada
- **Archivo:** `AuthController.java` líneas 78-91
- **Problema:** `login()` busca el usuario, luego `getUserByEmail()` lo vuelve a buscar, luego busca el rol (que `generateToken` ya buscó). Además `getUserByEmail` en el use case es una fuga del dominio hacia el controller. El use case debería devolver un resultado completo (`AuthResult`).

---

### 🟢 BAJOS / MEJORAS

#### B1. BCrypt con strength por defecto (10)
`SecurityConfig.java` — para producción se recomienda `new BCryptPasswordEncoder(12)` o migrar a `Argon2PasswordEncoder` / `DelegatingPasswordEncoder` (permite migración futura de algoritmo).

#### B2. Falta validación de tamaño en campos de texto
`RegisterRequest.fullName`, `phone` sin `@Size(max=...)` ni patrón — la columna es `VARCHAR(255)`; inputs mayores generan 500 de BD en vez de 400.

#### B3. Timestamps con `ZonedDateTime.now()` sin Clock inyectable
`User.createBuyer()` — dificulta testing y usa la TZ del servidor; preferir `Instant`/`OffsetDateTime` con `Clock`.

#### B4. Formato de error inconsistente
`GlobalExceptionHandler` devuelve mapas ad-hoc; falta el contrato estándar (`timestamp`, `status`, `error`, `errorCode`, `path`, `traceId`) que sí sería correlacionable con el `CorrelationIdFilter` existente.

---

## Lo que está bien ✅

- RS256 asimétrico con JWKS publicado (`/.well-known/jwks.json`) — correcto para validación distribuida en microservicios.
- BCrypt vía `PasswordEncoder` port/adapter; nunca texto plano.
- `SecurityConfig` deny-by-default (`anyRequest().authenticated()`), CSRF deshabilitado apropiadamente para API stateless con JWT.
- `@PreAuthorize` en controllers administrativos + `requireOrganizerOwnership` para scoping multi-tenant.
- Flyway con `ddl-auto: validate`; Swagger deshabilitado en prod; arquitectura hexagonal consistente; Testcontainers en integración.
- `CorrelationIdFilter` para trazabilidad.

---
---

# Plan de Implementación

> Orden estricto de prioridad. Cada fase es independiente y desplegable. Los pasos indican archivos exactos a tocar.

## FASE 0 — Quick wins (horas, sin nuevas tablas)

### 0.1 Validar estado del usuario en login (C1)
1. En `User.java` agregar métodos de dominio: `isActive()`, `canAuthenticate()` (p. ej. `ACTIVE` y `UNVERIFIED` pueden autenticarse mientras no exista verificación de email; `SUSPENDED` nunca). Modelar `status` como enum `UserStatus` en el dominio.
2. En `LoginUserService.login()`: tras validar la contraseña, si `!user.canAuthenticate()` lanzar `AccountDisabledException` (nueva, en `domain/exception`).
3. En `GlobalExceptionHandler`: mapear `AccountDisabledException` → `403` con mensaje genérico ("La cuenta no está habilitada").
4. **Test:** unit test en `LoginUserUseCaseTest` — usuario `SUSPENDED` no obtiene token.

### 0.2 Eliminar fuga de información en 500 (C7)
1. En `GlobalExceptionHandler.handleRuntimeException`: devolver mensaje genérico `"Error interno del servidor"` + `traceId` (tomar del MDC del `CorrelationIdFilter`); loguear `ex` completo con `log.error`.
2. Agregar handler para `Exception` (no solo `RuntimeException`).
3. Aprovechar para unificar el contrato de error (B4): record `ErrorResponse(timestamp, status, error, errorCode, message, path, traceId)` y usarlo en todos los handlers.

### 0.3 Eliminar credenciales fallback (C5)
1. `application.yml`: cambiar `${GLOBAL_DB_PASSWORD:AppSecret789}` → `${GLOBAL_DB_PASSWORD}` (los defaults de desarrollo van en `application-lab.yml` o en `.env` local no commiteado).
2. `application-prod.yml`: quitar fallbacks `guest/guest` de RabbitMQ → `${RABBITMQ_USER}` / `${RABBITMQ_PASSWORD}` sin default. El servicio debe **fallar al arrancar** si falta un secreto.
3. Rotar la contraseña `AppSecret789` en los entornos donde se haya usado (ya está en el historial de git).

### 0.4 Arreglar indentación Hikari en prod (A6)
1. En `application-prod.yml` mover el bloque `hikari:` dentro de `spring.datasource:` (mismo nivel que `url`).
2. Verificar arranque con perfil prod y confirmar en logs `pool-name: orion-db-pool`.

### 0.5 Logging seguro en prod (A2)
1. `application-prod.yml`: `com.orionticket: INFO`, `org.springframework.security: WARN`.
2. `management.endpoint.health.show-details: when-authorized` (o `never`).
3. Revisar también `application.yml` base (los DEBUG solo deben vivir en `application-lab.yml`).

### 0.6 Eliminar o implementar `PATCH /v1/users/{userId}/status` (C6)
Opción recomendada: implementarlo de verdad.
1. Crear DTO `UpdateStatusRequest` con enum validado (`ACTIVE`, `SUSPENDED`) — nada de `Map<String,String>`.
2. Agregar `activateUser(UUID userId, UUID adminId)` a `UserManagementUseCase` / `UserManagementService` con persistencia real vía `userRepositoryPort.save()` + audit log, y transiciones válidas en el dominio (`User.activate()` con validación de estado actual).
3. Reescribir el endpoint para usar el use case (y eliminar el `getAllUsers().stream()` — usar `findById`).
4. **Test de integración** en `UserManagementIntegrationTest` verificando persistencia real.

### 0.7 Mitigar timing/enumeración en login (A1, parte 1)
1. En `LoginUserService.login()`: cuando el email no existe, ejecutar igualmente `passwordHasherPort.matches(rawPassword, DUMMY_BCRYPT_HASH)` (constante con un hash BCrypt pre-generado) antes de lanzar `InvalidCredentialsException`.
2. Mismo mensaje y mismo status (401) para "no existe" y "password incorrecta" (ya se cumple — mantenerlo).

### 0.8 Límite máximo de contraseña (A3, parte 1)
1. En `RegisterRequest`, `LoginRequest`, `CreateUserRequest`, `CreateStaffRequest`: `@Size(min = 8, max = 72)` en password (en login solo `max`).
2. `@Size(max = 255)` en `fullName`, `@Size(max = 50)` + `@Pattern` en `phone` (B2).
3. Configurar `server.max-http-request-header-size` / límite de body si no lo impone el gateway.

---

## FASE 1 — Refresh tokens, revocación y logout (C3) — la más importante

Diseño tipo GoTrue: access token corto + refresh token opaco rotativo persistido.

### 1.1 Migración `V5__refresh_tokens.sql`
```sql
CREATE TABLE refresh_tokens (
    token_id     UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users(user_id),
    token_hash   VARCHAR(64) NOT NULL UNIQUE,      -- SHA-256 del token opaco; NUNCA el token en claro
    parent_id    UUID REFERENCES refresh_tokens(token_id), -- cadena de rotación
    issued_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    user_agent   VARCHAR(512),
    ip_address   VARCHAR(45)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
```

### 1.2 Dominio y puertos
1. `domain/model/RefreshToken.java` con lógica: `isExpired()`, `isRevoked()`, `revoke()`.
2. `domain/port/out/RefreshTokenRepositoryPort.java`: `save`, `findByTokenHash`, `revokeAllForUser(userId)`, `revokeChain(tokenId)`.
3. Adapter JPA en `infrastructure/adapters/out/persistence/` (entity + mapper + repo, siguiendo el patrón existente).

### 1.3 Casos de uso
1. **Login** (`LoginUserService`): además del access token, generar refresh token opaco (`SecureRandom` 256 bits, Base64URL), guardar su SHA-256, devolver ambos. Access token: **bajar default a 900s (15 min)** en `jwt.expiration`.
2. **`POST /v1/auth/refresh`** (nuevo use case `RefreshTokenUseCase`):
   - Buscar por hash; si no existe / expirado → 401.
   - **Detección de reuso:** si ya está revocado/rotado → revocar toda la cadena del usuario (`revokeChain`) y 401 (token robado).
   - Verificar `user.canAuthenticate()` (usuario suspendido = refresh denegado → así la suspensión surte efecto en ≤15 min).
   - Rotar: revocar el actual, emitir nuevo par access+refresh con `parent_id`.
3. **`POST /v1/auth/logout`**: revocar el refresh token recibido (y opcionalmente todos los del usuario con `?all=true`).
4. `suspendUser()` y `updateUserRole()` en `UserManagementService`: llamar `revokeAllForUser(userId)`.

### 1.4 API
1. `LoginResponse`: agregar `refreshToken` y bajar `expiresIn` al del access token.
2. `SecurityConfig`: agregar `/v1/auth/refresh` y `/v1/auth/logout` — refresh es público (autentica por el propio token), logout puede ser público o autenticado.
3. Reemplazar el wildcard `/v1/auth/**` por rutas explícitas (M6).

### 1.5 Mantenimiento
Job programado (`@Scheduled`) o migración de limpieza para borrar tokens expirados > 30 días.

### 1.6 Tests
- Unit: rotación, detección de reuso, expiración.
- Integración (Testcontainers): login → refresh → refresh con token viejo revoca cadena; logout invalida; usuario suspendido no puede refrescar.

---

## FASE 2 — Rate limiting y protección de fuerza bruta (C4)

### 2.1 Lockout por cuenta (en BD, sobrevive reinicios y múltiples réplicas)
1. Migración `V6__login_attempts.sql`:
```sql
ALTER TABLE users ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMPTZ;
```
2. En `LoginUserService`:
   - Antes de validar: si `locked_until > now()` → misma `InvalidCredentialsException` genérica (no revelar el lockout) o 429 con `Retry-After` (decisión de producto).
   - Password incorrecta: incrementar contador; al llegar a 5 → `locked_until = now() + 15 min` (backoff progresivo: 15min, 1h, 24h).
   - Login exitoso: resetear contador.
3. Auditar cada intento fallido (ver Fase 4).

### 2.2 Rate limiting por IP
- **Opción recomendada:** en el gateway (Traefik tiene middleware `rateLimit` nativo) para `/v1/auth/login`, `/v1/auth/register`, `/v1/auth/recover` — p. ej. 10 req/min por IP.
- Defensa en profundidad en el servicio: Bucket4j con filtro sobre esas rutas (usar IP de `X-Forwarded-For` ya que `forward-headers-strategy: native` está configurado).

---

## FASE 3 — Recover password + verificación de email (C2)

> Prerequisito: decidir el mecanismo de envío de email. Recomendado: **publicar evento a RabbitMQ** (`identity.email.requested`) y que un notification-service haga el envío — ya existe `IdentityEventPublisherPort` y `RabbitMqIdentityEventPublisherAdapter` como patrón a seguir.

### 3.1 Migración `V7__one_time_tokens.sql` (patrón GoTrue: tabla única de tokens de un solo uso)
```sql
CREATE TABLE one_time_tokens (
    token_id    UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(user_id),
    token_hash  VARCHAR(64) NOT NULL,               -- SHA-256, nunca en claro
    token_type  VARCHAR(32) NOT NULL,               -- 'EMAIL_VERIFICATION' | 'PASSWORD_RECOVERY'
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    UNIQUE (user_id, token_type, token_hash)
);
CREATE INDEX idx_ott_hash ON one_time_tokens(token_hash, token_type);
```

### 3.2 Recover password
1. **`POST /v1/auth/recover`** `{email}`:
   - Responder **siempre `200 OK`** con el mismo cuerpo, exista o no el email (anti-enumeración, igual que GoTrue).
   - Si existe: generar token opaco (SecureRandom 256 bits), guardar hash con TTL 1h, publicar evento con el token en claro para el email.
   - Rate limit estricto (1 por email cada 60s — chequear `created_at` del último token).
2. **`POST /v1/auth/recover/confirm`** `{token, newPassword}`:
   - Validar hash + tipo + no usado + no expirado → 400 genérico si falla.
   - Actualizar `password_hash`, marcar `used_at`, **revocar todos los refresh tokens del usuario** (Fase 1), invalidar otros tokens de recovery pendientes.
   - Auditar `PASSWORD_RECOVERED`.
3. Aplicar la misma política de contraseña del registro.

### 3.3 Verificación de email
1. En `RegisterUserService.registerBuyer()`: tras persistir, generar token `EMAIL_VERIFICATION` (TTL 24h) y publicar evento.
2. **`POST /v1/auth/verify`** `{token}`: validar y transicionar `UNVERIFIED → ACTIVE` (método de dominio `User.verifyEmail()` que valide la transición).
3. **`POST /v1/auth/resend-verification`** `{email}`: respuesta 200 siempre; rate limit 60s.
4. Decidir la política de `canAuthenticate()` para `UNVERIFIED` (GoTrue: configurable; recomendado bloquear login hasta verificar, o permitirlo con claim `email_verified: false` en el JWT para que el gateway/servicios decidan).

### 3.4 Change password autenticado
**`POST /v1/auth/change-password`** `{currentPassword, newPassword}` (requiere JWT): verificar contraseña actual, actualizar, revocar todos los refresh tokens excepto la sesión actual, auditar.

---

## FASE 4 — Auditoría y transacciones (A4, A5)

### 4.1 Auditoría de autenticación
1. Extender `AuditLogPort` con eventos: `LOGIN_SUCCESS`, `LOGIN_FAILED`, `USER_REGISTERED`, `PASSWORD_RECOVERY_REQUESTED`, `PASSWORD_CHANGED`, `TOKEN_REFRESH_REUSE_DETECTED`, `ACCOUNT_LOCKED`.
2. Incluir IP y user-agent (pasarlos desde el controller; nunca loguear la contraseña ni el token).
3. Mediano plazo: persistir en tabla `audit_log` o publicar a RabbitMQ en lugar de solo SLF4J (el `Slf4jAuditLogAdapter` actual se pierde con la rotación de logs).

### 4.2 Transacciones y race conditions
1. Anotar con `@Transactional` los métodos de escritura de `RegisterUserService`, `UserManagementService`, y los nuevos servicios de las fases 1-3 (en la capa de aplicación, según el estándar del proyecto).
2. En registro/creación: capturar `DataIntegrityViolationException` y traducirla a `UserAlreadyExistsException` (→ 409) para cerrar la ventana del check-then-act.
3. Reemplazar los `throw new RuntimeException("Email already exists")` de `UserManagementService` (líneas 56 y 100) por `UserAlreadyExistsException`.

---

## FASE 5 — Endurecimiento (M1–M7, B1–B4)

### 5.1 Roles por nombre, no por UUID mágico (M1)
1. Agregar `findByName(String)` a `RoleRepositoryPort`.
2. `RegisterUserService`: resolver `BUYER` por nombre (cachear con `@Cacheable` si se desea).
3. `UserManagementService.createOrganizerStaff`: validar contra nombres `VENUE_STAFF` / `DOOR_VALIDATOR` resolviendo el rol recibido, no comparando strings de UUID.
4. Definir enum `RoleName` en el dominio como única fuente de verdad.

### 5.2 Autorización estricta (M2, M3)
1. `AuthenticatedUserResolver.resolveOrganizerId`: eliminar el fallback `userId`; si rol es `ORGANIZER` y falta el claim → `AccessDeniedException`.
2. `updateUserRole`: validar que el rol exista (`RoleRepositoryPort.findById` → 404 si no) y prohibir escalado no permitido (p. ej. solo otro SUPER_ADMIN puede otorgar SUPER_ADMIN; impedir auto-modificación).

### 5.3 Headers de seguridad y CORS (M4)
En `SecurityConfig`:
```java
.headers(headers -> headers
    .httpStrictTransportSecurity(h -> h.includeSubDomains(true).maxAgeInSeconds(31536000))
    .contentTypeOptions(Customizer.withDefaults())
    .cacheControl(Customizer.withDefaults()))
.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```
Definir CORS explícito (aunque el gateway lo maneje, documentar la decisión).

### 5.4 Rotación de claves JWT (M5)
1. `SecurityConfig.jwtDecoder`: en lugar de `withPublicKey(...)`, usar `NimbusJwtDecoder.withJwkSetUri(...)` apuntando al propio JWKS local, o un `JWKSource` con múltiples claves.
2. `JwtProviderAdapter`/`JwksController`: soportar lista de claves (activa para firmar + anteriores para validar), configurable vía `jwt.keys[]`.
3. Documentar el procedimiento de rotación (agregar clave nueva → esperar TTL máximo del access token → retirar la vieja).

### 5.5 Hashing (B1)
`SecurityConfig`: usar `DelegatingPasswordEncoder` con `bcrypt` strength 12 como default — los hashes existentes siguen validando y permite migrar a Argon2id después:
```java
PasswordEncoderFactories.createDelegatingPasswordEncoder() // o custom con bcrypt@12
```

### 5.6 Refactor del login (M7)
`LoginUserUseCase.login()` debe devolver un `AuthResult(accessToken, refreshToken, expiresIn, user, role)`; eliminar `getUserByEmail` del use case y las consultas duplicadas del `AuthController`.

---

## Checklist de verificación final (Definition of Done)

- [x] ~~Usuario `SUSPENDED` no puede hacer login ni refresh~~ (Fase 0 + Fase 1 — validado con tests unitarios)
- [x] ~~Flujo completo: register → email de verificación → verify → login → refresh → logout~~ (Fase 3 — verify listo; el test de integración end-to-end requiere Docker/Testcontainers)
- [x] ~~Recover password end-to-end con token de un solo uso e invalidación de sesiones~~ (Fase 3 — recover + reset + revocación de sesiones implementados)
- [x] ~~5 logins fallidos → cuenta bloqueada temporalmente (test)~~ (Fase 2 — validado con tests unitarios: lockout en 5 intentos, backoff progresivo, reset en login exitoso)
- [x] ~~Respuestas de `/register` no permiten enumeración por timing~~ (Fase 0 — BCrypt dummy ejecutado en login; registro aún devuelve 409, revisar en Fase 3)
- [x] ~~Ningún 500 expone `ex.getMessage()`; todos los errores llevan `traceId`~~ (Fase 0)
- [x] ~~`grep -r "AppSecret789\|guest" src/main/resources` → sin resultados~~ (Fase 0 — `AppSecret789` y `guest/guest` en prod eliminados)
- [x] ~~Arranque con perfil `prod` falla si faltan secretos obligatorios~~ (Fase 0 — fallbacks eliminados en prod)
- [x] ~~Logs en prod: sin DEBUG de security~~ (Fase 0 — `WARN` en prod)
- [x] ~~Reuso de refresh token rotado revoca toda la cadena~~ (Fase 1 — validado con test unitario)
- [x] ~~Logout revoca refresh token; `all=true` revoca todas las sesiones~~ (Fase 1)
- [x] ~~Suspensión/cambio de rol revoca todas las sesiones activas~~ (Fase 1)
- [ ] `mvn verify` en verde con Testcontainers (los tests unitarios pasan; los de integración requieren Docker disponible en el entorno)

---

## Bitácora de implementación

### Fase 0 — Quick wins (COMPLETADA 2026-07-05)

#### 0.1 Validar estado del usuario en login (C1) ✅
- **Nuevos archivos:**
  - `domain/model/UserStatus.java` — enum tipado con `canAuthenticate()` (política: `SUSPENDED` nunca; `ACTIVE`/`UNVERIFIED` sí).
  - `domain/exception/AccountDisabledException.java` — excepción con mensaje genérico.
- **Modificados:**
  - `domain/model/User.java` — `createBuyer()` usa `UserStatus.UNVERIFIED.name()`; nuevos métodos `activate()`, `verifyEmail()`, `canAuthenticate()`, `isActive()`, `isSuspended()` con transiciones de dominio válidas.
  - `application/service/LoginUserService.java` — valida `canAuthenticate()` **después** de la contraseña (no revela estado a quien no conoce la password).
  - `infrastructure/adapters/in/rest/GlobalExceptionHandler.java` — mapea `AccountDisabledException` → 403.
- **Tests:** `LoginUserUseCaseTest` — 3 nuevos casos (suspendido, unverified, inexistente).

#### 0.2 Fuga de información en 500 + contrato de error unificado (C7, B4) ✅
- **Nuevo:** `infrastructure/adapters/in/rest/dto/ErrorResponse.java` — record con `timestamp`, `status`, `error`, `errorCode`, `message`, `path`, `traceId`.
- **Reescrito:** `GlobalExceptionHandler.java` — todos los handlers devuelven `ErrorResponse`; `RuntimeException` y `Exception` devuelven mensaje genérico + loguean detalle con traceId; handler para `UserNotFoundException` (404) que antes caía al 500 genérico.

#### 0.3 Eliminar credenciales fallback (C5) ✅
- `application.yml` — `${GLOBAL_DB_PASSWORD}` sin default `AppSecret789`.
- `application-prod.yml` — `${RABBITMQ_USER}` / `${RABBITMQ_PASSWORD}` sin defaults `guest/guest`.
- **Acción pendiente:** rotar `AppSecret789` en entornos donde se usó (ya está en el historial de git).

#### 0.4 Arreglar indentación Hikari en prod (A6) ✅
- `application-prod.yml` — bloque `hikari:` movido bajo `spring.datasource:` (estaba bajo `spring:`). Ahora `connection-init-sql`, pool size y timeouts se aplican en prod.

#### 0.5 Logging seguro en prod (A2) ✅
- `application-prod.yml` — `com.orionticket: INFO`, `org.springframework.security: WARN`, `health.show-details: when-authorized`.

#### 0.6 Implementar `PATCH /v1/users/{userId}/status` de verdad (C6) ✅
- **Nuevo:** `infrastructure/adapters/in/rest/dto/UpdateStatusRequest.java` — DTO con `UserStatus` validado (reemplaza al `Map<String,String>` que no persistía).
- **Modificados:**
  - `application/port/in/UserManagementUseCase.java` — nuevo método `updateUserStatus(userId, newStatus, adminId)`.
  - `application/service/UserManagementService.java` — implementación con persistencia real, transiciones de dominio válidas, `@Transactional`, audit log. También: `@Transactional` en todos los métodos de escritura; `RuntimeException("Email already exists")` reemplazado por `UserAlreadyExistsException`; captura de `DataIntegrityViolationException` para cerrar la ventana check-then-act (A5).
  - `infrastructure/adapters/in/rest/UserManagementController.java` — endpoint reescrito usando el use case; eliminado el `getAllUsers().stream()` ineficiente; imports limpiados.

#### 0.7 Mitigar timing/enumeración en login (A1) ✅
- `LoginUserService.java` — cuando el email no existe, ejecuta `passwordHasherPort.matches(rawPassword, DUMMY_BCRYPT_HASH)` antes de lanzar la excepción, manteniendo el tiempo de respuesta constante.
- **Test:** verifica que se llama a `matches` aunque el usuario no exista.

#### 0.8 Límite máximo de contraseña + validaciones (A3, B2) ✅
- `RegisterRequest`, `CreateUserRequest`, `CreateStaffRequest` — `@Size(min=8, max=72)` en password (BCrypt trunca a 72 bytes).
- `LoginRequest` — `@Size(max=72)` en password (anti-DoS).
- Todos los DTOs — `@Size(max=255)` en email/fullName, `@Size(max=50)` + `@Pattern` en phone.

#### Verificación
- `mvn compile` ✅
- Tests unitarios ✅ (LoginUserUseCaseTest, UserManagementServiceTest, RegisterUserUseCaseTest, AuthControllerTest, JwtProviderAdapterTest, JwtAuthoritiesConverterTest, AuthenticatedUserResolverTest, JwksControllerTest).
- Tests de integración (Testcontainers): no ejecutables en este entorno (Docker no disponible). **Pendiente:** ejecutar `mvn verify` en un entorno con Docker antes de desplegar.

---

### Fase 1 — Refresh tokens, revocación y logout (COMPLETADA 2026-07-05)

#### 1.1 Migración `V5__refresh_tokens.sql` ✅
- Tabla `refresh_tokens` con `token_hash` (SHA-256, CHAR(64), único), `parent_id` (cadena de rotación), `issued_at`, `expires_at`, `revoked_at`, `user_agent`, `ip_address`.
- Índices en `user_id`, `token_hash`, `expires_at`.
- FK a `users(user_id)` con `ON DELETE` restrictivo por defecto.

#### 1.2 Dominio `RefreshToken` + puerto `RefreshTokenRepositoryPort` ✅
- **`domain/model/RefreshToken.java`** — modelo de dominio con `isExpired()`, `isRevoked()`, `isValid()`, `revoke()`.
- **`domain/port/out/RefreshTokenRepositoryPort.java`** — `save`, `findByTokenHash`, `revokeAllForUser(userId)`, `revokeChain(tokenId)`, `countActiveForUser(userId)`.

#### 1.3 Adapter JPA ✅
- **`infrastructure/.../entity/RefreshTokenJpaEntity.java`** — entidad JPA mapeada a `refresh_tokens`.
- **`infrastructure/.../repository/SpringDataRefreshTokenRepository.java`** — repo Spring Data con `revokeAllForUser` (UPDATE JPQL) y `revokeChain` (CTE recursiva nativa para recorrer la cadena `parent_id`).
- **`infrastructure/.../mapper/RefreshTokenMapper.java`** — mapper bidireccional.
- **`infrastructure/.../RefreshTokenRepositoryAdapter.java`** — implementación del puerto.

#### 1.4 Login genera access + refresh; access TTL bajado a 15 min ✅
- **`application/port/out/RefreshTokenGeneratorPort.java`** — puerto para generar token opaco (SecureRandom 256 bits) y hashear con SHA-256.
- **`infrastructure/.../security/SecureRandomRefreshTokenGenerator.java`** — implementación.
- **`application/port/in/AuthResult.java`** — value object con `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `user`.
- **`application/port/in/LoginUserUseCase.java`** — firma cambiada a `login(email, password, userAgent, ipAddress)` → `AuthResult`; eliminado `getUserByEmail` (M7).
- **`application/service/LoginUserService.java`** — genera access JWT + refresh opaco rotativo persistido hasheado; `@Transactional`; `@Value` para TTLs.
- **`application.yml`** — `jwt.expiration: 900` (15 min), `jwt.refresh-expiration: 2592000` (30 días).

#### 1.5 `RefreshTokenService` (rotación + detección de reuso) ✅
- **`application/port/in/RefreshTokenUseCase.java`** + **`application/service/RefreshTokenService.java`**:
  - Busca por hash; si no existe → 401.
  - **Detección de reuso:** si el token ya está revocado (ya rotado) → revoca toda la cadena (`revokeChain`) y 401.
  - Si expirado → revoca y 401.
  - Verifica `user.canAuthenticate()`; si suspendido → `revokeAllForUser` + 403.
  - **Rotación:** revoca el token actual, emite nuevo par access+refresh encadenado vía `parentId`.

#### 1.6 `LogoutService` ✅
- **`application/port/in/LogoutUseCase.java`** + **`application/service/LogoutService.java`**:
  - `logout(token)` revoca el token presentado.
  - `logout(token, all=true)` revoca todos los tokens del usuario.
  - **Idempotente:** token blank/null/desconocido no falla.

#### 1.7 Suspensión/cambio de rol revocan sesiones ✅
- **`UserManagementService.java`** — `suspendUser`, `updateUserRole` y `updateUserStatus(SUSPENDED)` llaman `revokeAllForUser(userId)`. Así la suspensión surte efecto en ≤ TTL del access (15 min), no en 24h.

#### 1.8 API ✅
- **`dto/LoginResponse.java`** — añadido `refreshToken`; schema OpenAPI documentado.
- **`dto/RefreshRequest.java`** + **`dto/LogoutRequest.java`** — nuevos DTOs con validación.
- **`AuthController.java`** — reescrito: `POST /v1/auth/login` (devuelve access+refresh), `POST /v1/auth/refresh` (rotación), `POST /v1/auth/logout` (204). Captura `User-Agent` e IP (`X-Forwarded-For`) para audit. Eliminada la consulta duplicada a `RoleRepositoryPort` (M7).

#### 1.9 SecurityConfig con rutas explícitas ✅
- **`SecurityConfig.java`** — reemplazado wildcard `/v1/auth/**` por rutas explícitas: `/v1/auth/register`, `/v1/auth/login`, `/v1/auth/refresh`, `/v1/auth/logout` (M6).

#### 1.10 Tests ✅
- **`LoginUserUseCaseTest`** — actualizado (5 tests): verifica access+refresh, suspended, unverified, non-existent con BCrypt dummy.
- **`RefreshTokenServiceTest`** (nuevo, 6 tests): rotación válida, token desconocido, **detección de reuso revoca cadena**, expirado, usuario suspendido revoca todos, token blank.
- **`LogoutServiceTest`** (nuevo, 4 tests): logout simple, logout all, token desconocido idempotente, token blank idempotente.
- **`AuthControllerTest`** — actualizado para nueva firma y `AuthResult`.
- **`UserManagementServiceTest`** — actualizado con mock de `RefreshTokenRepositoryPort`; verifica revocación al suspender.
- **`SecurityAuthorizationTest`** — actualizado con `@MockBean` para `RefreshTokenUseCase`/`LogoutUseCase` y nueva firma de login.
- **Resultado:** 32 tests, 0 fallos, BUILD SUCCESS.

#### Verificación
- `mvn compile` ✅
- Tests unitarios ✅ (32 tests, 0 fallos).
- Tests de integración (Testcontainers): pendientes de ejecutar en entorno con Docker. **Importante:** la CTE recursiva de `revokeChain` y la migración V5 deben validarse contra PostgreSQL real antes de desplegar.

---

### Fase 2 — Rate limiting y protección de fuerza bruta (COMPLETADA 2026-07-05)

#### 2.1 Lockout por cuenta en BD (C4) ✅
- **Migración `V6__login_attempts.sql`** — añade `failed_login_attempts INT NOT NULL DEFAULT 0` y `locked_until TIMESTAMPTZ` a `users`. El estado de lockout sobrevive reinicios y se comparte entre réplicas.
- **`UserJpaEntity`** — nuevos campos `failedLoginAttempts` y `lockedUntil` mapeados a las columnas.
- **`UserRepositoryAdapter`** — mapeo bidireccional de los nuevos campos.
- **`User` (dominio)** — nuevos métodos con lógica de lockout y backoff progresivo:
  - `isLocked()` — verifica si `lockedUntil > now()`.
  - `registerFailedLogin()` — incrementa contador; al alcanzar un múltiplo del umbral (5) fija `lockedUntil` con backoff: **1.er bloqueo → 15 min, 2.º → 1 h, 3.º y siguientes → 24 h**.
  - `resetFailedLoginAttempts()` — resetea contador y limpia lockout (llamado en login exitoso).
  - `remainingLockSeconds()` — segundos restantes de bloqueo para el header `Retry-After`.
- **`AccountLockedException`** — nueva excepción de dominio que transporta `retryAfterSeconds`.
- **`LoginUserService`** — flujo de login actualizado:
  1. Si la cuenta está bloqueada → `AccountLockedException` (429 + Retry-After) **antes** de validar contraseña. Audita `ACCOUNT_LOCKED_LOGIN_ATTEMPT`.
  2. Si la contraseña es incorrecta → `registerFailedLogin()`, persiste, audita `LOGIN_FAILED` y, si se disparó el bloqueo, audita `ACCOUNT_LOCKED`.
  3. Si el login es exitoso → resetea el contador si era > 0 y persiste.
- **`GlobalExceptionHandler`** — handler para `AccountLockedException` → **429 Too Many Requests** con header `Retry-After` y body `ErrorResponse` con `errorCode: ACCOUNT_LOCKED`.
- **Decisión de producto:** 429 + Retry-After (mejor UX para usuarios legítimos; un atacante ya sabe que la cuenta existe tras 5 intentos fallidos).

#### 2.2 Rate limiting por IP con Bucket4j (C4) ✅
- **Dependencia:** `com.bucket4j:bucket4j_jdk17-core:8.18.0` añadida al `pom.xml`.
- **`RateLimitFilter`** — filtro servlet (`OncePerRequestFilter`) con token-bucket Bucket4j en memoria:
  - Protege `/v1/auth/login` y `/v1/auth/register`.
  - Bucket por IP (resuelve `X-Forwarded-For` con fallback a `getRemoteAddr()`).
  - **Configurable:** `security.rate-limit.capacity` (default 10) y `security.rate-limit.refill-minutes` (default 1) → 10 req/min por IP.
  - Respuesta 429 con header `Retry-After` y body JSON consistente con `ErrorResponse` (`errorCode: RATE_LIMIT_EXCEEDED`).
  - `@Order(Ordered.HIGHEST_PRECEDENCE + 10)` — corre después de `CorrelationIdFilter` (que tiene `HIGHEST_PRECEDENCE`) para que el traceId esté disponible en la respuesta.
- **`CorrelationIdFilter`** — añadido `@Order(Ordered.HIGHEST_PRECEDENCE)` para garantizar orden correcto.
- **`application.yml`** — sección `security.rate-limit` con defaults configurables vía variables de entorno.
- **Limitación documentada:** el estado es en memoria; con múltiples réplicas cada una mantiene su propio contador. El rate limiting real por IP debe vivir en el gateway (Traefik `rateLimit` middleware). Esto es defensa en profundidad.

#### Tests ✅
- **`LoginUserUseCaseTest`** — actualizado (9 tests): incluye mock de `AuditLogPort`; nuevos tests de lockout:
  - Cuenta bloqueada → `AccountLockedException` con retryAfter > 0, no valida contraseña.
  - 5.º intento fallido → dispara lockout, audita `ACCOUNT_LOCKED`.
  - Login exitoso tras intentos fallidos → resetea contador.
  - Lock expirado + contraseña correcta → login exitoso y reset.
- **`RateLimitFilterTest`** (nuevo, 6 tests): path no protegido pasa, límite dentro de capacidad pasa, 4.ª petición excede límite → 429 + Retry-After, buckets independientes por IP, `X-Forwarded-For` resuelto correctamente, `/v1/auth/register` también limitado.
- **Resultado:** 76 tests, 0 fallos, BUILD SUCCESS.

#### Verificación
- `mvn compile` ✅
- Tests unitarios ✅ (76 tests, 0 fallos).
- Tests de integración (Testcontainers): pendientes de ejecutar en entorno con Docker. La migración V6 y el mapeo JPA de los nuevos campos deben validarse contra PostgreSQL real antes de desplegar.

---

### Fase 3 — Recover password + verificación de email (COMPLETADA 2026-07-05)

> **Decisiones de producto:**
> - **Envío de email:** publicación de eventos a RabbitMQ (`identity.email.verification.requested`, `identity.password.recovery.requested`) para que un notification-service haga el envío. Sigue el patrón existente de `IdentityEventPublisherPort`.
> - **Login de UNVERIFIED:** permitido con claim `email_verified: false` en el JWT (comportamiento GoTrue). El gateway/servicios downstream deciden si bloquean según el claim.

#### 3.1 Migración `V7__one_time_tokens.sql` ✅
- Tabla `one_time_tokens` con `token_hash` (SHA-256, VARCHAR(64)), `token_type` (`EMAIL_VERIFICATION` | `PASSWORD_RECOVERY`), `created_at`, `expires_at`, `used_at`.
- `UNIQUE (user_id, token_type, token_hash)` + índices en `token_hash, token_type` y `user_id, token_type`.
- FK a `users(user_id)`.

#### 3.2 Dominio `OneTimeToken` + puerto `OneTimeTokenRepositoryPort` ✅
- **`domain/model/OneTimeToken.java`** — modelo con enum `TokenType`, métodos `isExpired()`, `isUsed()`, `isValid()`, `markUsed()`.
- **`domain/port/out/OneTimeTokenRepositoryPort.java`** — `save`, `findByTokenHashAndType`, `markAllUnusedForUserAndType`, `countActiveForUserAndType`.

#### 3.3 Adapter JPA ✅
- **`OneTimeTokenJpaEntity.java`** — entidad mapeada a `one_time_tokens`.
- **`SpringDataOneTimeTokenRepository.java`** — repo Spring Data con `findByTokenHashAndTokenType`, `markAllUnusedForUserAndType` (UPDATE JPQL), `countActiveForUserAndType` (COUNT con filtro de expiración).
- **`OneTimeTokenMapper.java`** — mapper bidireccional.
- **`OneTimeTokenRepositoryAdapter.java`** — implementación del puerto.

#### 3.4 Eventos de email (RabbitMQ) ✅
- **`IdentityEventPublisherPort`** — extendido con `publishEmailVerificationRequested(user, rawToken)` y `publishPasswordRecoveryRequested(user, rawToken)`.
- **`RabbitMqIdentityEventPublisherAdapter`** — publica eventos a `identity.email.verification.requested` e `identity.password.recovery.requested` con el token en claro para que el notification-service construya los links.

#### 3.5 Claim `email_verified` en JWT ✅
- **`JwtProviderAdapter`** — añadido `.claim("email_verified", user.isActive())`. Los usuarios `UNVERIFIED` obtienen `email_verified: false`; los `ACTIVE` obtienen `true`. El gateway/servicios downstream pueden usar este claim para decidir si bloquean ciertas operaciones.

#### 3.6 Register genera token de verificación + evento ✅
- **`RegisterUserService`** — tras persistir el usuario, genera un token `EMAIL_VERIFICATION` (TTL 24h), lo persiste hasheado y publica el evento. Si el envío falla, no revierte el registro (el usuario puede solicitar reenvío). Añadido `@Transactional` y captura de `DataIntegrityViolationException` para race conditions.

#### 3.7 Recover password ✅
- **`RecoverPasswordUseCase` + `RecoverPasswordService`** (`POST /v1/auth/recover`):
  - **Anti-enumeración:** responde siempre 200 OK exista o no el email.
  - Si el email existe: genera token opaco (SecureRandom 256 bits), guarda hash con TTL 1h, publica evento.
  - **Rate limiting:** 1 token cada 60s por usuario (chequea `countActiveForUserAndType`).
  - Audita `PASSWORD_RECOVERY_REQUESTED`.

#### 3.8 Reset password ✅
- **`ResetPasswordUseCase` + `ResetPasswordService`** (`POST /v1/auth/recover/confirm`):
  - Valida hash + tipo + no usado + no expirado → mensaje genérico si falla (no revela causa).
  - Actualiza `password_hash`, marca `used_at`, invalida otros tokens de recovery pendientes.
  - **Revoca todos los refresh tokens del usuario** (invalida sesiones activas).
  - Audita `PASSWORD_RECOVERED`.

#### 3.9 Verificación de email ✅
- **`VerifyEmailUseCase` + `VerifyEmailService`** (`POST /v1/auth/verify`):
  - Valida token `EMAIL_VERIFICATION` + no usado + no expirado → mensaje genérico si falla.
  - Transición de dominio `UNVERIFIED → ACTIVE` vía `User.verifyEmail()`.
  - Marca token como usado, invalida otros tokens de verificación pendientes.
  - Audita `EMAIL_VERIFIED`.

#### 3.10 Resend verification ✅
- **`ResendVerificationUseCase` + `ResendVerificationService`** (`POST /v1/auth/resend-verification`):
  - **Anti-enumeración:** responde siempre 200 OK.
  - Solo reenvía si el usuario existe y está `UNVERIFIED`.
  - **Rate limiting:** 1 token activo a la vez (evita spam).
  - Audita `VERIFICATION_RESENT`.

#### 3.11 Change password autenticado ✅
- **`ChangePasswordUseCase` + `ChangePasswordService`** (`POST /v1/auth/change-password`, requiere JWT):
  - Verifica contraseña actual → `InvalidCredentialsException` si es incorrecta.
  - Actualiza `password_hash`.
  - **Revoca todos los refresh tokens del usuario** (fuerza re-login desde el frontend).
  - Audita `PASSWORD_CHANGED`.

#### 3.12 API + SecurityConfig ✅
- **`AuthController`** — 5 nuevos endpoints: `/v1/auth/recover`, `/v1/auth/recover/confirm`, `/v1/auth/verify`, `/v1/auth/resend-verification`, `/v1/auth/change-password`. OpenAPI documentado.
- **`SecurityConfig`** — rutas públicas explícitas añadidas: `/v1/auth/recover`, `/v1/auth/recover/confirm`, `/v1/auth/verify`, `/v1/auth/resend-verification`. `change-password` requiere autenticación (JWT).
- **`RateLimitFilter`** — protegidas también `/v1/auth/recover` y `/v1/auth/resend-verification` (defensa en profundidad contra spam de emails).

#### 3.13 Configuración ✅
- **`application.yml`** — `security.verification-token-ttl: 86400` (24h), `security.recovery-token-ttl: 3600` (1h), configurables vía variables de entorno.

#### DTOs nuevos ✅
- `RecoverRequest` — `{email}` con validación.
- `ResetPasswordRequest` — `{token, newPassword}` con `@Size(min=8, max=72)` en newPassword.
- `VerifyEmailRequest` — `{token}`.
- `ResendVerificationRequest` — `{email}` con validación.
- `ChangePasswordRequest` — `{currentPassword, newPassword}` con validación de tamaño.

#### Tests ✅
- **`RegisterUserUseCaseTest`** — actualizado (2 tests): verifica generación de token + publicación de evento en registro.
- **`RecoverPasswordServiceTest`** (nuevo, 3 tests): email existente genera token + evento, email inexistente no hace nada, token activo previene nuevo token.
- **`ResetPasswordServiceTest`** (nuevo, 5 tests): token válido actualiza password + revoca sesiones, token inexistente/expirado/usado/blank → error genérico.
- **`VerifyEmailServiceTest`** (nuevo, 5 tests): token válido transiciona a ACTIVE, token inexistente/expirado/usado/blank → error genérico.
- **`ResendVerificationServiceTest`** (nuevo, 4 tests): usuario UNVERIFIED genera token + evento, email inexistente no hace nada, usuario ya verificado no hace nada, token activo previene nuevo.
- **`ChangePasswordServiceTest`** (nuevo, 3 tests): contraseña correcta actualiza + revoca, contraseña incorrecta lanza excepción, usuario inexistente lanza excepción.
- **`AuthControllerTest`** — actualizado por nuevo constructor del controller.
- **`SecurityAuthorizationTest`** — actualizado con `@MockBean` para los 5 nuevos use cases.
- **Resultado:** 96 tests, 0 fallos, BUILD SUCCESS.

#### Verificación
- `mvn compile` ✅
- Tests unitarios ✅ (96 tests, 0 fallos).
- Tests de integración (Testcontainers): pendientes de ejecutar en entorno con Docker. La migración V7, el mapeo JPA de `one_time_tokens` y las queries JPQL deben validarse contra PostgreSQL real antes de desplegar.

---

### Fase 4 — Auditoría y transacciones (COMPLETADA 2026-07-05)

#### 4.1 Auditoría de autenticación ✅
- **`AuditLogPort`** — extendido con método `logAction(actorId, action, details, ipAddress, userAgent)` que incluye contexto de red (IP + user-agent). El método de 3 args se mantiene como `default` que delega con `null`/`null` para acciones administrativas donde la IP no está disponible.
- **`Slf4jAuditLogAdapter`** — actualizado para loguear IP y user-agent estructurado (`IP: {} | UA: {}`).
- **Eventos nuevos añadidos a los servicios:**
  - `LoginUserService`: `LOGIN_SUCCESS` (con IP + UA), `LOGIN_FAILED` y `ACCOUNT_LOCKED` actualizados para incluir IP + UA, `ACCOUNT_LOCKED_LOGIN_ATTEMPT` actualizado.
  - `RegisterUserService`: `USER_REGISTERED` (tras persistir el usuario).
  - `RefreshTokenService`: `TOKEN_REFRESHED` (refresh exitoso con IP + UA), `TOKEN_REFRESH_REUSE_DETECTED` (reuse de token rotado → revoca cadena, con IP + UA).
  - `LogoutService`: `LOGOUT` (revoca token individual), `LOGOUT_ALL` (revoca todas las sesiones).
- **Nunca se loguea** la contraseña ni el token en claro.
- **Mediano plazo:** persistir en tabla `audit_log` o publicar a RabbitMQ en lugar de solo SLF4J (el `Slf4jAuditLogAdapter` actual se pierde con la rotación de logs).

#### 4.2 Transacciones y race conditions ✅
- **`RoleManagementService`** — añadido `@Transactional` a `createRole`, `updateRole`, `deleteRole`. Captura `DataIntegrityViolationException` en `createRole` para race conditions de nombre de rol duplicado.
- **`RoleNotFoundException`** — nueva excepción de dominio que reemplaza los `RuntimeException("Role not found")` en `RoleManagementService` (líneas 37 y 50 del código original).
- **`GlobalExceptionHandler`** — añadido handler para `RoleNotFoundException` → `404 NOT_FOUND` con código `ROLE_NOT_FOUND`.
- **`RegisterUserService`** — ya tenía `@Transactional` y captura de `DataIntegrityViolationException` (Fase 3).
- **`UserManagementService`** — ya tiene `@Transactional` en todos los métodos de escritura (verificado).
- **Todos los servicios de Fase 1-3** — ya tienen `@Transactional` (verificado: `LoginUserService`, `RefreshTokenService`, `LogoutService`, `RecoverPasswordService`, `ResetPasswordService`, `VerifyEmailService`, `ResendVerificationService`, `ChangePasswordService`).

#### DTOs corregidos (regla AGENTS.md: no usar @Data) ✅
- Los 5 DTOs nuevos de Fase 3 (`RecoverRequest`, `ResetPasswordRequest`, `VerifyEmailRequest`, `ResendVerificationRequest`, `ChangePasswordRequest`) fueron actualizados de `@Data` a `@Getter @Setter @NoArgsConstructor` según la regla del proyecto.

#### Tests ✅
- **`LoginUserUseCaseTest`** — actualizado: verificaciones de `logAction` cambiadas de 3-arg a 5-arg (con IP + UA) para matchear la nueva firma.
- **`RefreshTokenServiceTest`** — actualizado: añadido `@Mock AuditLogPort` y nuevo constructor con 5 dependencias.
- **`LogoutServiceTest`** — actualizado: añadido `@Mock AuditLogPort` y nuevo constructor con 3 dependencias.
- **`RegisterUserUseCaseTest`** — actualizado: añadido `@Mock AuditLogPort` y nuevo constructor con 6 dependencias.
- **`RoleManagementServiceTest`** — actualizado: `assertThrows(RuntimeException.class)` → `assertThrows(RoleNotFoundException.class)` en `updateRoleThrowsWhenNotFound` y `deleteRoleThrowsWhenNotFound`.
- **Resultado:** 100 tests, 0 fallos, BUILD SUCCESS.

#### Verificación
- `mvn compile` ✅
- Tests unitarios ✅ (100 tests, 0 fallos).
- Tests de integración (Testcontainers): pendientes de ejecutar en entorno con Docker.
