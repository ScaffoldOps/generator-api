# Generator API

OpenAPI source: `src/main/resources/openapi/generator-api.yaml`

Base paths:
- API: `/api/generator/v1`
- Actuator: `/actuator`

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
