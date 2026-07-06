# Generator API

OpenAPI source: `src/main/resources/openapi/generator-api.yaml`

Base paths:
- API: `/api/generator/v1`
- Actuator: `/api/generator/v1/actuator`

Authentication:
- `POST /generation-requests`, `DELETE /generation-requests/{id}`, `GET /generation-requests`, and `GET /generation-requests/{id}` require a bearer JWT
- Actuator endpoints are public
- Swagger UI and OpenAPI JSON endpoints are public

Swagger UI:
- `/api/generator/v1/swagger-ui.html`

OpenAPI JSON:
- `/api/generator/v1/v3/api-docs`

Health:
- `/api/generator/v1/actuator/health`
- `/api/generator/v1/actuator/health/liveness`
- `/api/generator/v1/actuator/health/readiness`

API endpoints:
- `POST /api/generator/v1/generation-requests`
- `DELETE /api/generator/v1/generation-requests/{id}`
- `GET /api/generator/v1/generation-requests/{id}`
- `GET /api/generator/v1/generation-requests`
- `PATCH /api/generator/v1/internal/generation-requests/{requestId}/status`

Create request body:
- `name`: required, non-empty
- `template`: required, non-empty
- `deploymentTarget`: required, currently `KUBERNETES`
- `database`, `restApi`, `security`, `messaging`: optional booleans describing requested capabilities

Response fields:
- `status`: one of `RECEIVED`, `GENERATING`, `GENERATED`, `DEPLOYING`, `DEPLOYED`, `FAILED`
- `message`, `artifactRef`, and `imageRef`: nullable generation result metadata
- `createdAt` and `updatedAt`: RFC 3339 timestamps

Delete cleanup behavior:
- `DELETE /generation-requests/{id}` removes the request record from PostgreSQL
  and publishes `artifact-cleanup-requested` to Kafka when the record existed.
- The topic defaults to `artifact-cleanup-requested` and can be overridden with
  `ARTIFACT_CLEANUP_REQUESTED_TOPIC`.
- Cleanup is asynchronous and eventually consistent, not transactional with the
  database delete.
- `generator-api` owns request lifecycle state and the deletion API, but it
  must not access the `generator-worker` PVC directly. `generator-worker` owns
  generated artifacts and PVC cleanup.

Error responses:
- `400`: validation failure, malformed JSON, or invalid UUID path parameter
- `401`: missing or invalid bearer token
- `403`: authenticated but not authorized
- `404`: request id not found for get or delete
- `500`: unexpected server error
