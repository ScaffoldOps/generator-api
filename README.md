# Generator API

## Purpose
`generator-api` is the ScaffoldOps microservice responsible for receiving and storing scaffold generation requests.

## What This Service Does
- Accepts generation requests over REST
- Validates request payloads
- Persists requests in PostgreSQL
- Publishes a `generation-requested` Kafka event after a request is stored
- Accepts internal generation lifecycle callbacks from `generator-worker`
- Secures API endpoints with JWT bearer authentication
- Exposes create, delete, get-by-id, and list endpoints for generation requests

## What This Service Does Not Do
- Does not execute generation jobs
- Does not deploy generated services
- Does not run `deployment-worker`

## Architecture
Hexagonal (ports and adapters):
- `domain`: request model and enums
- `application`: use cases and repository port
- `infrastructure`: JPA persistence adapter, Kafka publisher, and runtime configuration
- `presentation`: OpenAPI-backed controllers, mappers, and API errors

Dependency direction:
`presentation/infrastructure -> application -> domain`

## Main Tech Stack
- Java 17
- Spring Boot 3
- Spring Web, Validation, Data JPA, Actuator
- Spring Security OAuth2 Resource Server
- Spring Kafka
- PostgreSQL (H2 in tests)
- OpenAPI Generator + springdoc
- Maven

## Run
Start PostgreSQL locally or port-forward the shared cluster service:

```bash
kubectl -n scaffoldops port-forward svc/postgres 5432:5432
```

Then start the app locally with the `local` Spring profile:

```bash
SPRING_PROFILES_ACTIVE=local \
DB_PASSWORD=... \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://localhost:8080/realms/scaffoldops-dev \
./mvnw spring-boot:run
```

Local development uses the PostgreSQL service through `kubectl port-forward`,
so the application connects to `localhost:5432` while the port-forward session
is active.
The dev Kubernetes deployment activates the `dev` Spring profile, which points
to `postgres.scaffoldops.svc.cluster.local:5432` from inside the cluster.
`DB_PASSWORD` must be provided from the environment locally and from a
Kubernetes secret in dev. `DB_USER` defaults to `generatorapiuser`.
Kafka defaults to `localhost:9092` locally and can be overridden with
`KAFKA_BOOTSTRAP_SERVERS`. For the MVP, DEV validates JWT signatures against
the `keycloak-dev` service and the `scaffoldops-dev` realm by using the JWKS
endpoint directly:

```bash
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://keycloak-dev.security.svc.cluster.local:8080/realms/scaffoldops-dev/protocol/openid-connect/certs
```

DEV intentionally does not validate `issuer-uri`, so tokens generated from a
local Keycloak port-forward such as `http://localhost:8080/realms/scaffoldops-dev`
are accepted as long as they are signed by the `scaffoldops-dev` realm keys.
Local startup therefore requires both a reachable Kafka broker and JWKS
endpoint.

## Test
```bash
./mvnw test
```

## API Reference
See [docs/API.md](docs/API.md).
OpenAPI source: `src/main/resources/openapi/generator-api.yaml`.

## Local End-to-End Demo

See [docs/local-demo.md](docs/local-demo.md) for the manual
`generator-api -> Kafka -> generator-worker -> Docker -> callback` flow.
The guide also documents the worker callback contract required before running
the demo. `deployment-worker` is intentionally excluded.

## Runtime Endpoints
- API base path: `/api/generator/v1`
- Example API endpoint: `/api/generator/v1/generation-requests`
- Swagger UI: `/api/generator/v1/swagger-ui.html`
- OpenAPI JSON: `/api/generator/v1/v3/api-docs`
- Actuator health: `/api/generator/v1/actuator/health`
- Liveness probe: `/api/generator/v1/actuator/health/liveness`
- Readiness probe: `/api/generator/v1/actuator/health/readiness`

All generation request endpoints require a bearer JWT. Actuator, Swagger UI,
and OpenAPI JSON endpoints are public. The Kafka topic used for request
publication defaults to `generation-requested` and can be overridden with
`GENERATION_REQUESTED_TOPIC`.

## Docker
```bash
./mvnw clean package -DskipTests
docker build -f Dockerfile -t scaffoldops/generator-api:latest .
```

## Kubernetes
Deployment assets live under `k8s/`:
- `k8s/deployment`

Shared PostgreSQL infrastructure is managed in `platform-infra`. This repository only owns the generator-api application manifests and runtime configuration.

For the MVP only DEV is active. Use `keycloak-dev` with realm
`scaffoldops-dev`; PRE can stay scaled down:

```bash
kubectl -n security scale deploy/keycloak-pre --replicas=0
```
