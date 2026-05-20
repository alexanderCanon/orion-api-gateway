# Development Phases & Roadmap

This document outlines the current implementation phase of the Orion API Gateway and the roadmap for future enhancements.

---

## Phase 1: Standalone Gateway & Tailscale Integration (Current Phase)

- **Objective**: Decouple the API Gateway from the main docker-compose environment and make it an independent, standalone service routing traffic using Tailscale private node IPs.
- **Implemented Features**:
  - Independent repository with unified Maven Wrapper.
  - Multi-stage optimized Docker build (`Dockerfile`) and production orchestration (`compose.yml`).
  - Edge Routing Layer using Traefik with automated HTTPS (Let's Encrypt certificates).
  - Path-based routing for all `v1` services (Identity, Events, Seating, Orders, Payments, Tickets, Notifications).
  - Environment profiles separation (`local` vs `prod`).
  - Spring Boot 3.5.14 and Spring Cloud 2025.0.0.

---

## Phase 2: Security & Rate Limiting (Next Phase)

- **Objective**: Protect upstream microservices by handling authorization and traffic control at the gateway edge.
- **Proposed Features**:
  - **JWT Validation Filter**: Decrypt and validate client JSON Web Tokens (issued by the Identity service) at the gateway before forwarding requests.
  - **Redis Rate Limiting**: Deploy Redis and configure `RequestRateLimiter` filters using token bucket algorithm to prevent brute force and DDoS attacks.
  - **CORS Configuration**: Centralized CORS rules configuration at the gateway.

---

## Phase 3: Service Discovery & Dynamic Config

- **Objective**: Eliminate static IP configurations and enable dynamic routing.
- **Proposed Features**:
  - **Eureka Client Integration**: Connect the gateway to a Eureka Service Discovery server to automatically discover microservice instances (e.g. routing using `lb://identity-service`).
  - **Spring Cloud Config Client**: Load configurations dynamically from a centralized Config Server.

---

## Phase 4: Resilience & Circuit Breakers

- **Objective**: Prevent cascading failures and ensure system stability.
- **Proposed Features**:
  - **Resilience4j Filters**: Configure circuit breakers and time-limiters for unstable upstreams.
  - **Fallback Routes**: Direct requests to fallback static endpoints or cache layers when a downstream service is down.
  - **Retry Filters**: Automatic retry policies for transient network errors.

---

## Phase 5: Advanced Observability

- **Objective**: Full visibility into system health, performance metrics, and log tracing.
- **Proposed Features**:
  - **OpenTelemetry & Micrometer**: Instrument the gateway to collect request latency and HTTP metrics.
  - **Distributed Tracing**: Propagate trace IDs (`W3C Trace Context`) to track end-to-end request lifecycles across microservices.
  - **Grafana & Prometheus**: Expose Actuator prometheus endpoints for visualization.
