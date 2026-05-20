# Deployment & Setup Guide

This document describes how to configure, build, and deploy the Orion API Gateway in local and production environments.

## Prerequisites

- **Java**: JDK 21 (for local builds).
- **Docker & Docker Compose**: Installed on the deployment server.
- **Tailscale**: The host must be connected to your Tailscale network to access upstream microservice IPs (e.g. `100.64.x.x`).

---

## 1. Local Development Setup

To run the application locally outside of Docker:

### Configure environment variables
Copy the `.env.example` file to `.env`:
```sh
cp .env.example .env
```
Open `.env` and configure the upstream URLs to your local mock servers or development environments.

### Run with Maven
Run the Spring Boot application specifying the `local` profile:
```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
This enables the properties defined in [application-local.yml](file:///home/alexavers/projects/gateway/gateway/src/main/resources/application-local.yml), which output verbose `DEBUG` logs for routes and enable read-write access to the gateway endpoints under Actuator.

---

## 2. Production Deployment (Docker Compose)

The production setup uses a multi-stage Docker build and a Traefik edge router container.

### Step 1: Create production `.env`
Create a `.env` file on your production host:
```sh
SERVER_PORT=8080
TRAEFIK_DASHBOARD_PORT=8081
TRAEFIK_HTTP_PORT=80
TRAEFIK_HTTPS_PORT=443
GATEWAY_HOST=api.orionticket.com
LETSENCRYPT_EMAIL=ops@orionticket.com

# Production Tailscale Node IPs
IDENTITY_SERVICE_URL=http://100.64.0.11:8081
EVENT_MANAGEMENT_SERVICE_URL=http://100.64.0.12:8082
SEATING_INVENTORY_SERVICE_URL=http://100.64.0.13:8083
ORDERS_SERVICE_URL=http://100.64.0.14:8084
PAYMENTS_SERVICE_URL=http://100.64.0.15:8085
TICKET_ISSUANCE_SERVICE_URL=http://100.64.0.16:8086
NOTIFICATIONS_SERVICE_URL=http://100.64.0.17:8088
```

### Step 2: Validate configuration
Verify the compose settings:
```sh
docker compose config
```

### Step 3: Launch containers
Build the gateway image and launch Traefik and the gateway in detached mode:
```sh
docker compose up -d --build
```
This builds the container image defined in [Dockerfile](file:///home/alexavers/projects/gateway/gateway/Dockerfile) and runs the application using the default `prod` settings.

---

## 3. Health & Monitoring

The gateway exposes Actuator endpoints for health check monitoring:

- **Health Check**:
  ```sh
  curl http://localhost:8080/actuator/health
  ```
  Returns `{"status":"UP","groups":["liveness","readiness"]}` when online.
- **Gateway Routes Info (Actuator)**:
  ```sh
  curl http://localhost:8080/actuator/gateway/routes
  ```
  Returns the active routing map inside the gateway. (In production, this is exposed as `read-only`).
