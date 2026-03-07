# Generator API

## Purpose
`generator-api` is the ScaffoldOps microservice responsible for receiving and storing scaffold generation requests.

## What This Service Does
- Accepts generation requests over REST
- Validates request payloads
- Persists requests in PostgreSQL
- Exposes request status retrieval by id and list endpoints

## What This Service Does Not Do
- Does not execute generation jobs
- Does not deploy generated services
- Does not publish Kafka events yet
- Does not enforce authentication yet

## Architecture
Hexagonal (ports and adapters):
- `domain`: request model and enums
- `application`: use cases and repository port
- `infrastructure`: JPA adapter and persistence mapping
- `presentation`: OpenAPI-backed controllers, mappers, and API errors

Dependency direction:
`presentation/infrastructure -> application -> domain`

## Main Tech Stack
- Java 17
- Spring Boot 3
- Spring Web, Validation, Data JPA, Actuator
- PostgreSQL (H2 in tests)
- OpenAPI Generator + springdoc
- Maven

## Run
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Test
```bash
./mvnw test
```

## API Reference
See [docs/API.md](docs/API.md).
OpenAPI source: `src/main/resources/openapi/generator-api.yaml`.
Swagger UI is available at `/swagger-ui.html`.

## Docker
```bash
./mvnw clean package -DskipTests
docker build -f Dockerfile -t scaffoldops/generator-api:latest .
```

## Kubernetes
Deployment assets live under `k8s/`:
- `k8s/config`
- `k8s/db`
- `k8s/deployment`
