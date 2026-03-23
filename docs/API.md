# Generator API

OpenAPI source: `src/main/resources/openapi/generator-api.yaml`

Base paths:
- API: `/api/generator/v1`
- Actuator: `/actuator`

Authentication:
- `POST /generation-requests`, `GET /generation-requests`, and `GET /generation-requests/{id}` require a bearer JWT
- Actuator endpoints are public
- Swagger UI and OpenAPI JSON endpoints are public

Swagger UI:
- `/api/generator/v1/swagger-ui.html`

OpenAPI JSON:
- `/api/generator/v1/v3/api-docs`

Health:
- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`

API endpoints:
- `POST /api/generator/v1/generation-requests`
- `GET /api/generator/v1/generation-requests/{id}`
- `GET /api/generator/v1/generation-requests`

Create request body:
- `name`: required, non-empty
- `template`: required, non-empty
- `deploymentTarget`: required, currently `KUBERNETES`
- `database`, `restApi`, `security`, `messaging`: optional booleans describing requested capabilities

Response fields:
- `status`: one of `RECEIVED`, `GENERATING`, `GENERATED`, `DEPLOYING`, `DEPLOYED`, `FAILED`
- `createdAt` and `updatedAt`: RFC 3339 timestamps

Error responses:
- `400`: validation failure, malformed JSON, or invalid UUID path parameter
- `401`: missing or invalid bearer token
- `403`: authenticated but not authorized
- `404`: request id not found
- `500`: unexpected server error
