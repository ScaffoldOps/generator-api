# Generator API

## Purpose
`generator-api` is the ScaffoldOps microservice responsible for receiving scaffold generation requests and orchestrating the independent generation and deployment lifecycles.

The previous TFG MVP generated services only: `generator-api` stored requests, published `generation-requested`, and `generator-worker` produced artifacts on a PVC. The TFM evolution introduces deployment as an explicit user action. Generation no longer implies deployment.

## What This Service Does
- Accepts generation requests over REST
- Validates request payloads
- Persists requests in PostgreSQL
- Stores `generationStatus` and `deploymentStatus` as independent lifecycle fields
- Publishes a `generation-requested` Kafka event after a request is stored
- Accepts explicit deployment requests through `POST /generation-requests/{id}/deployment`
- Accepts explicit undeployment requests through `DELETE /generation-requests/{id}/deployment`
- Publishes `deployment-requested` and `undeployment-requested` Kafka events for those user actions
- Publishes an `artifact-cleanup-requested` Kafka event after a request is deleted
- Accepts internal generation lifecycle callbacks from `generator-worker`
- Accepts internal deployment lifecycle callbacks from the future `deployment-worker`
- Secures API endpoints with JWT bearer authentication
- Exposes create, delete, get-by-id, and list endpoints for generation requests

## What This Service Does Not Do
- Does not execute generation jobs
- Does not deploy generated services
- Does not run `deployment-worker`
- Does not build container images automatically
- Does not mount or access the `generator-worker` PVC directly

## Lifecycle Model
Generation states:
`RECEIVED`, `GENERATING`, `GENERATED`, `GENERATION_FAILED`.

Deployment states:
`NOT_DEPLOYED`, `DEPLOYING`, `DEPLOYED`, `UNDEPLOYING`, `DEPLOYMENT_FAILED` (legacy requested states remain readable for existing rows).

Creating a generation request always sets:

```text
generationStatus = RECEIVED
deploymentStatus = NOT_DEPLOYED
```

`GENERATED` confirms that artifact generation/upload and Docker image build/push succeeded,
with nonblank `artifactRef` and `imageRef`. The lifecycle is
`RECEIVED -> GENERATING -> GENERATED` or `RECEIVED -> GENERATING -> GENERATION_FAILED`.
Deployment is a separate user action and requires `GENERATED` plus both references. Invalid or duplicate deployment and undeployment requests return `409 Conflict`.

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

In the Kubernetes MVP, `generator-api` persists the `artifactRef` received from
`generator-worker` as-is. That reference currently points to the worker pod
filesystem, backed by the worker PVC:

```text
file:///var/lib/generator-worker/manifests/<serviceName>-<requestId>/
```

Delete is permanent: `DELETE /generation-requests/{id}` removes the PostgreSQL row and publishes
`artifact-cleanup-requested` with `requestId`, `name`, `artifactRef`, `imageRef`,
`deploymentNamespace` (nullable), and `deletedAt`. HTTP 204 means accepted, not that
external cleanup has finished; 404 means the request was absent.

The worker independently removes the PVC workspace, MinIO object (or explicit trailing-slash
prefix), safe legacy file artifact, and configured Docker Hub tag. Cleanup is asynchronous
and best-effort. Undeploy/disable retains artifacts and images for restoration/redeployment.
The API never accesses MinIO, Docker Hub, or the worker PVC.

Deletion holds a database transaction and row lock until Kafka acknowledges publication
(up to 30 seconds). Publication failures roll back the row deletion. PostgreSQL and Kafka
are still not atomic: a crash/commit failure after acknowledgement, or a send timeout whose
message later succeeds, can clean assets while the row remains. No outbox was introduced.
Operators must reconcile such failures; HTTP 204 cannot guarantee external removal.
Deleting during active generation can also race with later asset creation; stop active work
before permanent deletion. Docker Hub cleanup requires worker credentials with delete permission
and explicit enablement; see generator-worker README.

In the TFM deployment flow, `generator-api` is prepared to publish:

```text
deployment-requested
undeployment-requested
```

These events contain request identity, service name, deployment target, an
`artifactRef` when available, and the request timestamp. They do not carry
artifact contents. Actual image building and Kubernetes deployment remain
outside this `generator-api` change.

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
`GENERATION_REQUESTED_TOPIC`. The deployment topics default to
`deployment-requested` and `undeployment-requested`, and can be overridden with
`DEPLOYMENT_REQUESTED_TOPIC` and `UNDEPLOYMENT_REQUESTED_TOPIC`. The artifact cleanup topic defaults to
`artifact-cleanup-requested` and can be overridden with
`ARTIFACT_CLEANUP_REQUESTED_TOPIC`.

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

Generation events and callbacks use `generationStatus`. The worker accepts legacy
event `status` during migration. Diagnostics `message`, `failureStage`, and
`retryCount` describe failures without adding user-facing statuses. Image
build/push retries default to three attempts with a fixed one-second backoff.

### Automatic image recovery

The API scheduler recovers only `GENERATION_FAILED` rows with a nonblank artifact, no image,
and `IMAGE_BUILD` or `IMAGE_PUSH` failure. It never requests deployment.

Configuration under `scaffoldops.generation.recovery`:

| Key | Default |
| --- | --- |
| enabled | true |
| fixed-delay | 5m |
| max-retries | 5 |
| batch-size | 10 |
| reservation-timeout | 30m |

Environment overrides: `GENERATION_RECOVERY_ENABLED`, `GENERATION_RECOVERY_FIXED_DELAY`,
`GENERATION_RECOVERY_MAX_RETRIES`, `GENERATION_RECOVERY_BATCH_SIZE`, `GENERATION_RECOVERY_RESERVATION_TIMEOUT`.
`app.kafka.topics.image-build-retry-requested` defaults to `image-build-retry-requested`.
Its payload contains `generationRequestId`, `name`, `template`, `database`, `restApi`, `security`,
`messaging`, `deploymentTarget`, `artifactRef`, `retryAttempt`, and `failureStage`.

PostgreSQL `FOR UPDATE SKIP LOCKED` reserves a batch in one short transaction before Kafka publication.
The existing `GENERATION_FAILED` status remains visible; a private reservation timestamp excludes in-flight rows.
Callbacks clear the reservation. A crashed publisher/worker can be retried after the reservation timeout;
set this longer than the worst expected Kafka queue delay plus build/push time.
Kafka delivery is at least once, so a redelivery may repeat a build of the same deterministic image tag.
Publication failures consume a reserved attempt and expire normally; even outages cannot cause unbounded scheduling.
`retryCount` is cumulative: existing worker retries count toward the configured maximum, and each scheduler
reservation adds one. Worker-local attempts during recovery do not increase it again.

For existing databases apply `docs/image-recovery.sql` before starting the API with schema validation.
The local SQL initializer applies the same idempotent change. No cleanup is required. NULL retry counts
are treated as zero; rows without an artifact or an eligible failure stage are deliberately ignored.

Deployment MVP: see [API lifecycle documentation](docs/API.md#mvp-deployment-lifecycle). Use POST and DELETE `/generation-requests/{id}/deployment`; undeploy retains generated assets for redeploy.
