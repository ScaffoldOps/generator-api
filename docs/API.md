# Generator API

OpenAPI source: `src/main/resources/openapi/generator-api.yaml`

Base paths:
- API: `/api/generator/v1`
- Actuator: `/api/generator/v1/actuator`

Authentication:
- generation request endpoints require a bearer JWT
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
- `POST /api/generator/v1/generation-requests/{id}/deploy`
- `DELETE /api/generator/v1/generation-requests/{id}/deployment`
- `GET /api/generator/v1/generation-requests/{id}`
- `GET /api/generator/v1/generation-requests`
- `PATCH /api/generator/v1/internal/generation-requests/{requestId}/generation-status`
- `PATCH /api/generator/v1/internal/generation-requests/{requestId}/deployment-status`

Create request body:
- `name`: required, non-empty
- `template`: required, non-empty
- `deploymentTarget`: required, currently `KUBERNETES`
- `database`, `restApi`, `security`, `messaging`: optional booleans describing requested capabilities

Response fields:
- `generationStatus`: one of `RECEIVED`, `GENERATING`, `GENERATED`, `GENERATION_FAILED`
- `deploymentStatus`: one of `NOT_DEPLOYED`, `DEPLOYMENT_REQUESTED`, `DEPLOYING`, `DEPLOYED`, `UNDEPLOYMENT_REQUESTED`, `UNDEPLOYING`, `DEPLOYMENT_FAILED`
- `message`, `artifactRef`, and `imageRef`: nullable generation result metadata
- `createdAt` and `updatedAt`: RFC 3339 timestamps

Lifecycle behavior:
- `POST /generation-requests` creates the request with `generationStatus=RECEIVED` and `deploymentStatus=NOT_DEPLOYED`, then publishes `generation-requested`.
- `POST /generation-requests/{id}/deploy` is accepted only when `generationStatus=GENERATED` and deployment is not already requested, running, deployed, or undeploying. It sets `deploymentStatus=DEPLOYMENT_REQUESTED` and publishes `deployment-requested`.
- `DELETE /generation-requests/{id}/deployment` is accepted only when `deploymentStatus=DEPLOYED`. It sets `deploymentStatus=UNDEPLOYMENT_REQUESTED` and publishes `undeployment-requested`.
- Invalid lifecycle actions return `409 Conflict`.
- Internal generation callbacks can update only `generationStatus`; internal deployment callbacks can update only `deploymentStatus`.

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
- `409`: invalid lifecycle transition or duplicate deploy/undeploy request
- `500`: unexpected server error
