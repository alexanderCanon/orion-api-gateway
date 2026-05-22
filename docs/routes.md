# Route Mapping & Upstream Services

This document lists the routes configured in the Spring Cloud Gateway and how they map to downstream microservices.

## Gateway Routes Table

The gateway filters incoming requests by their path prefix and routes them to the appropriate backend service using Tailscale node IPs.

Protected routes require a Bearer JWT issued by Identity and validated through `IDENTITY_JWKS_URI`. The public application paths are `/v1/auth/**` and `/actuator/health`.

| Route ID | Path Patterns | Target Upstream Variable | Default Fallback (Local) |
| :--- | :--- | :--- | :--- |
| **identity-service** | `/v1/auth/**`, `/v1/users/**`, `/v1/roles/**`, `/v1/organizers/**` | `IDENTITY_SERVICE_URL` | `http://localhost:8081` |
| **event-management-service** | `/v1/events/**`, `/v1/venues/**`, `/v1/catalog/**` | `EVENT_MANAGEMENT_SERVICE_URL` | `http://localhost:8082` |
| **seating-inventory-service** | `/v1/seats/**`, `/v1/reservations/**`, `/v1/batches/**`, `/v1/availability/**` | `SEATING_INVENTORY_SERVICE_URL` | `http://localhost:8083` |
| **orders-service** | `/v1/orders/**`, `/v1/checkout/**` | `ORDERS_SERVICE_URL` | `http://localhost:8084` |
| **payments-service** | `/v1/payments/**`, `/v1/payouts/**` | `PAYMENTS_SERVICE_URL` | `http://localhost:8085` |
| **ticket-issuance-service** | `/v1/tickets/**`, `/v1/buyers/*/tickets` | `TICKET_ISSUANCE_SERVICE_URL` | `http://localhost:8086` |
| **notifications-service** | `/v1/notifications/**` | `NOTIFICATIONS_SERVICE_URL` | `http://localhost:8088` |

## Filters

### PreserveHostHeader
By default, the gateway is configured with the `PreserveHostHeader` filter. This ensures that the original `Host` header sent by the client (e.g. `api.orionticket.example`) is forwarded intact to downstream services, which is critical for services that generate redirect URIs or check request origins.

## Path Matching Rules

- **Ant Path Style**: The gateway uses Ant-style path matching (e.g., `**` matches any sub-path recursively, and `*` matches a single path segment).
- **Prefix Preservation**: Paths are forwarded exactly as received. For example, a request to `/v1/events/categories` is routed to the event service upstream as `/v1/events/categories` without path striping.
- **Specific Segment Matching**: The route `ticket-issuance-service` contains `/v1/buyers/*/tickets`, matching requests like `/v1/buyers/123/tickets` and forwarding them to the `TICKET_ISSUANCE_SERVICE_URL`.
