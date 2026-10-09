# generator-api

JWT-protected REST API and lifecycle system of record. Stores generation requests in PostgreSQL, publishes Kafka lifecycle events, accepts worker callbacks, and schedules image recovery.

Platform guides: [ScaffoldOps documentation](https://github.com/ScaffoldOps/scaffoldops-docs) · [local setup](https://github.com/ScaffoldOps/scaffoldops-docs/blob/main/docs/local-development.md) · [configuration](https://github.com/ScaffoldOps/scaffoldops-docs/blob/main/docs/configuration.md) · [known gaps](https://github.com/ScaffoldOps/scaffoldops-docs/blob/main/docs/findings.md).

## Local requirements

Java 17, Maven (wrapper included), a reachable Kafka broker, PostgreSQL and Keycloak.

Kafka port-forward alone does not fix broker metadata advertising `kafka:9092`; use a broker with a host-reachable advertised listener for host execution. The cluster path is the documented MVP setup.

## Configuration

| Variable | Use |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `local` for host execution; `dev` in the DEV cluster |
| `DB_PASSWORD` | Required database password; `DB_USER` defaults to `generatorapiuser` |
| `KAFKA_BOOTSTRAP_SERVERS` | Reachable broker; host default `localhost:9092` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | Local issuer, default `http://localhost:8080/realms/scaffoldops-dev` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | DEV uses the Keycloak DEV realm JWKS endpoint |
| `SERVER_PORT` | Default `8080`; use `8081` alongside local Keycloak |

Kubernetes reads `postgres-credentials/POSTGRES_GENERATOR_API_PASSWORD` in the application namespace. Topic overrides and scheduler settings are in `src/main/resources/application.yml`.

## Build, test and run

Run from this repository root after provisioning the dependencies and exporting the variables above:

```bash
./mvnw clean test
./mvnw clean package -DskipTests
SPRING_PROFILES_ACTIVE=local SERVER_PORT=8081 DB_PASSWORD="$DB_PASSWORD" ./mvnw spring-boot:run
```

## Docker and Kubernetes

```bash
docker build -f Dockerfile -t victodomvar/scaffoldops-generator-api:local .
```

Build the JAR before docker build. CI publishes `victodomvar/scaffoldops-generator-api` with `<Maven version>-<short SHA>` and `develop` or `main`; deployment uses the immutable tag. GitHub Actions requires repository/organization secrets `DOCKER_USERNAME` and `DOCKER_PASSWORD` (a Docker Hub access token).

After applying shared infrastructure and creating component secrets:

```bash
kubectl -n scaffoldops-dev apply -f k8s/deployment/generator-api-service.yaml
kubectl -n scaffoldops-dev apply -f k8s/deployment/generator-api-deployment.yaml
kubectl -n scaffoldops-dev rollout status deploy/generator-api-deployment
```

Deployment is `generator-api-deployment`; Service is `generator-api-service`, port 80 → 8080. The DEV manifest uses a namespace-local PostgreSQL Service; see the documented infrastructure gap before expecting it to start.

## GitHub Actions

- `pr-checks.yml`: Maven verification and tests for PRs to `develop`/`main` and feature branch pushes.
- `develop-pipeline.yml`: verify, test, build/push image, deploy to `scaffoldops-dev`.
- `main-pipeline.yml`: corresponding PRE pipeline targeting `scaffoldops-pre`.
- `deploy-k8s.yml`: reusable `workflow_call` deployment, selects context `minikube` and waits for rollout; it is not manually dispatchable.

Jobs use self-hosted runners. PRE needs additional infrastructure; see [delivery guide](https://github.com/ScaffoldOps/scaffoldops-docs/blob/main/docs/delivery.md).

## Troubleshooting

Check `kubectl config current-context`, pods, events and component logs before restarting. For `ImagePullBackOff`, check the image/tag and namespace-local registry secret. `docker-hub-credentials` is Opaque application configuration and must never be used as `imagePullSecrets`. WSL runners need Linux Docker, daemon access, and a readable kubeconfig; a WindowsApps Docker shim can cause EACCES. Port conflicts require changing the local side of the port-forward. See [operations](https://github.com/ScaffoldOps/scaffoldops-docs/blob/main/docs/operations.md) for commands and lifecycle diagnostics.
