# orion-api-gateway

API Gateway independiente para OrionTicket.

## Alcance actual

- Spring Boot 3.5.14.
- Spring Cloud Gateway WebFlux.
- Actuator con `health`, `info` y `gateway`.
- Dockerfile multi-stage.
- Compose con Traefik delante para HTTPS.
- Upstreams directos por IP Tailscale.

Fuera de alcance por ahora:

- Eureka / service discovery.
- Spring Cloud Config Server.
- RabbitMQ.
- Redis / rate limiting distribuido.
- Observabilidad avanzada / telemetry.
- Kubernetes.
- Access Control y Reporting.

## Servicios enrutados

| Ruta | Upstream |
|---|---|
| `/v1/auth/**`, `/v1/users/**`, `/v1/roles/**`, `/v1/organizers/**` | `IDENTITY_SERVICE_URL` |
| `/v1/events/**`, `/v1/venues/**`, `/v1/catalog/**` | `EVENT_MANAGEMENT_SERVICE_URL` |
| `/v1/seats/**`, `/v1/reservations/**`, `/v1/batches/**`, `/v1/availability/**` | `SEATING_INVENTORY_SERVICE_URL` |
| `/v1/orders/**`, `/v1/checkout/**` | `ORDERS_SERVICE_URL` |
| `/v1/payments/**`, `/v1/payouts/**` | `PAYMENTS_SERVICE_URL` |
| `/v1/tickets/**`, `/v1/buyers/*/tickets` | `TICKET_ISSUANCE_SERVICE_URL` |
| `/v1/notifications/**` | `NOTIFICATIONS_SERVICE_URL` |

## Preparacion del repo

Crear `.env` desde el ejemplo:

```sh
cp .env.example .env
```

Reemplazar los upstreams `100.64.0.x` por las IPs Tailscale reales en tu archivo `.env`.

## Verificacion local

```sh
./mvnw test
./mvnw package -DskipTests
docker compose --env-file .env config
docker compose --env-file .env up -d --build
curl http://localhost:8080/actuator/health
```

Para validar el Compose usando el ejemplo sin crear `.env`:

```sh
ORION_ENV_FILE=.env.example docker compose --env-file .env.example config
```

En VPS, Traefik debe exponer HTTPS usando `GATEWAY_HOST`.
