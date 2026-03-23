# Generator API

## Purpose
`generator-api` is the ScaffoldOps microservice responsible for receiving and storing scaffold generation requests.

## What This Service Does
- Accepts generation requests over REST
- Validates request payloads
- Persists requests in PostgreSQL
- Publishes a `generation-requested` Kafka event after a request is stored
- Secures API endpoints with JWT bearer authentication
- Exposes request status retrieval by id and list endpoints

## What This Service Does Not Do
- Does not execute generation jobs
- Does not deploy generated services
- Does not process asynchronous status updates from downstream workers yet

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
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://localhost:8090/realms/scaffoldops \
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
`KAFKA_BOOTSTRAP_SERVERS`. JWT validation uses the configured issuer URI and
must point to a reachable Keycloak realm or equivalent OIDC issuer. Local
startup therefore requires both a reachable Kafka broker and JWT issuer.

## Test
```bash
./mvnw test
```

## API Reference
See [docs/API.md](docs/API.md).
OpenAPI source: `src/main/resources/openapi/generator-api.yaml`.

## Runtime Endpoints
- API base path: `/api/generator/v1`
- Example API endpoint: `/api/generator/v1/generation-requests`
- Swagger UI: `/api/generator/v1/swagger-ui.html`
- OpenAPI JSON: `/api/generator/v1/v3/api-docs`
- Actuator health: `/actuator/health`
- Liveness probe: `/actuator/health/liveness`
- Readiness probe: `/actuator/health/readiness`

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
