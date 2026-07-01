# Local Generator End-to-End Demo

This demo validates the generation flow from `generator-api` to
`generator-worker`. It stops after image creation and lifecycle callback.
`deployment-worker` is intentionally out of scope.

## Expected Flow

1. `generator-api` stores a generation request in PostgreSQL.
2. `generator-api` publishes `generation-requested` to Kafka.
3. `generator-worker` consumes the event with consumer group
   `generator-worker`.
4. The worker reports `GENERATING` to `generator-api`.
5. The worker writes a Spring Boot Hello World project.
6. The worker runs `docker build` and creates
   `scaffoldops/<serviceName>:<requestId>`.
7. The worker reports `GENERATED`, `artifactRef`, and `imageRef`.
8. `GET /generation-requests/{requestId}` returns the persisted result.

The current worker may also publish `deployment-requested` after the image
build. This demo does not start a consumer for that topic and does not perform
deployment.

## Callback Contract

`generator-worker` uses this callback contract:

```http
PATCH /api/generator/v1/internal/generation-requests/{requestId}/status
Content-Type: application/json
Authorization: Bearer <service-token>
```

```json
{
  "status": "GENERATED",
  "message": "Generation and image build completed",
  "artifactRef": "file:///tmp/generator-worker/manifests/hello-demo-<requestId>/",
  "imageRef": "scaffoldops/hello-demo:<requestId>"
}
```

Implemented worker behavior:

- Uses HTTP `PATCH`.
- Sends `message` rather than the former `detail` field.
- Sends `GENERATING` before project generation.
- Sends `GENERATED` only after `docker build` succeeds.
- Includes `artifactRef` and `imageRef` in the successful callback.
- Sends `FAILED` when project generation or image build fails.
- Sends `GENERATOR_API_BEARER_TOKEN` as a Bearer token when configured.

Optional contract check:

```bash
rg -n 'HttpMethod.PATCH|artifactRef|imageRef|bearer-token' \
  ../generator-worker/src/main/java/com/scaffoldops/generatorworker/infrastructure/lifecycle \
  ../generator-worker/src/main/java/com/scaffoldops/generatorworker/application/service \
  ../generator-worker/src/main/resources
```

## Prerequisites

- Java 17
- Docker with a running daemon
- `curl`, `jq`, and `rg`
- A JWT in `TOKEN` accepted by `generator-api`
- The repositories checked out next to each other:
  `generator-api` and `generator-worker`

The JWT must be usable for the public POST/GET operations. The worker must use
an equivalent service token for the internal callback. Do not disable
authentication on the internal endpoint outside an isolated local profile.

## 1. Start PostgreSQL and Kafka

Create an isolated Docker network:

```bash
docker network create scaffoldops-demo 2>/dev/null || true
```

Start PostgreSQL:

```bash
docker run --rm -d \
  --name scaffoldops-demo-postgres \
  --network scaffoldops-demo \
  -p 5432:5432 \
  -e POSTGRES_DB=generatorapidb \
  -e POSTGRES_USER=generatorapiuser \
  -e POSTGRES_PASSWORD=generatorapipassword \
  postgres:16
```

Start a single-node Kafka broker with a host-accessible advertised listener:

```bash
docker run --rm -d \
  --name scaffoldops-demo-kafka \
  --network scaffoldops-demo \
  -p 9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT \
  -e KAFKA_LISTENERS=INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:29093 \
  -e KAFKA_ADVERTISED_LISTENERS=INTERNAL://scaffoldops-demo-kafka:29092,EXTERNAL://localhost:9092 \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=INTERNAL \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@scaffoldops-demo-kafka:29093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=true \
  -e CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk \
  confluentinc/cp-kafka:7.7.7
```

Wait for both dependencies:

```bash
until docker exec scaffoldops-demo-postgres pg_isready \
  -U generatorapiuser -d generatorapidb; do sleep 2; done

until docker exec scaffoldops-demo-kafka kafka-topics \
  --bootstrap-server scaffoldops-demo-kafka:29092 --list >/dev/null 2>&1; do sleep 2; done
```

Create the topics used by the worker:

```bash
docker exec scaffoldops-demo-kafka kafka-topics \
  --bootstrap-server scaffoldops-demo-kafka:29092 \
  --create --if-not-exists --topic generation-requested \
  --partitions 1 --replication-factor 1

docker exec scaffoldops-demo-kafka kafka-topics \
  --bootstrap-server scaffoldops-demo-kafka:29092 \
  --create --if-not-exists --topic deployment-requested \
  --partitions 1 --replication-factor 1
```

## 2. Start generator-api

Use port `8081`, matching the worker local profile:

```bash
cd ../generator-api

SPRING_PROFILES_ACTIVE=local \
SERVER_PORT=8081 \
DB_PASSWORD=generatorapipassword \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://localhost:8080/realms/scaffoldops-dev \
./mvnw spring-boot:run
```

In another terminal:

```bash
until curl -fsS http://localhost:8081/api/generator/v1/actuator/health \
  | jq -e '.status == "UP"'; do
  sleep 2
done
```

## 3. Observe Kafka

Start a console consumer before creating the request:

```bash
docker exec -it scaffoldops-demo-kafka kafka-console-consumer \
  --bootstrap-server scaffoldops-demo-kafka:29092 \
  --topic generation-requested \
  --from-beginning \
  --property print.key=true \
  --property key.separator=' | '
```

This terminal provides direct evidence that `generator-api` published the
event. The worker logs provide evidence that consumer group
`generator-worker` consumed it.

## 4. Start generator-worker

Use stable output directories so the generated project can be inspected:

```bash
cd ../generator-worker
rm -rf /tmp/scaffoldops-demo-worker

SPRING_PROFILES_ACTIVE=local \
SERVER_PORT=8082 \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
GENERATOR_API_LIFECYCLE_HTTP_ENABLED=true \
GENERATOR_API_BASE_URL=http://localhost:8081/api/generator/v1 \
GENERATOR_API_LIFECYCLE_STATUS_UPDATE_PATH=/internal/generation-requests/{requestId}/status \
GENERATOR_API_BEARER_TOKEN="$TOKEN" \
GENERATION_MANIFEST_OUTPUT_DIR=/tmp/scaffoldops-demo-worker/manifests \
GENERATION_HANDOFF_STATE_DIR=/tmp/scaffoldops-demo-worker/handoff \
DOCKER_COMMAND=docker \
./mvnw spring-boot:run
```

`GENERATOR_API_BEARER_TOKEN` is optional at configuration level, but required
when `generator-api` protects the internal endpoint, as it does by default.

## 5. Create a Generation Request

Use a unique service name because `generation_requests.name` is unique:

```bash
export SERVICE_NAME="hello-demo-$(date +%s)"

curl -fsS -X POST \
  http://localhost:8081/api/generator/v1/generation-requests \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"name\": \"$SERVICE_NAME\",
    \"template\": \"spring-boot-hexagonal\",
    \"database\": false,
    \"restApi\": true,
    \"security\": false,
    \"messaging\": false,
    \"deploymentTarget\": \"KUBERNETES\"
  }" | tee /tmp/scaffoldops-generation-request.json

export REQUEST_ID="$(
  jq -r '.id' /tmp/scaffoldops-generation-request.json
)"

test -n "$REQUEST_ID"
test "$REQUEST_ID" != "null"
```

The initial response must contain:

```json
{
  "status": "RECEIVED"
}
```

## 6. Verify Kafka Publication and Consumption

The Kafka console consumer must show a message whose key and `requestId` equal
`$REQUEST_ID`.

The worker log must include:

```text
Received generation-requested event ... requestId=<REQUEST_ID>
Processing generation request ... requestId=<REQUEST_ID>
```

Check the consumer group:

```bash
docker exec scaffoldops-demo-kafka kafka-consumer-groups \
  --bootstrap-server scaffoldops-demo-kafka:29092 \
  --describe --group generator-worker
```

For an idle completed demo, `LAG` should be `0`.

## 7. Verify the Generated Project

```bash
export PROJECT_DIR="/tmp/scaffoldops-demo-worker/manifests/$SERVICE_NAME-$REQUEST_ID"

test -f "$PROJECT_DIR/pom.xml"
test -f "$PROJECT_DIR/Dockerfile"
find "$PROJECT_DIR/src/main/java" -name 'HelloApplication.java' -print -quit | grep .
find "$PROJECT_DIR/src/main/java" -name 'HelloController.java' -print -quit | grep .
rg -n "Hello from $SERVICE_NAME" "$PROJECT_DIR/src/main/java"
```

## 8. Verify the Docker Image

```bash
export IMAGE_REF="scaffoldops/$SERVICE_NAME:$REQUEST_ID"

docker image inspect "$IMAGE_REF" >/dev/null
docker images --format '{{.Repository}}:{{.Tag}}' | grep -Fx "$IMAGE_REF"
```

The worker log must also include:

```text
Building generated Docker image ... imageName=<IMAGE_REF>
Built generated Docker image ... imageName=<IMAGE_REF>
```

## 9. Verify the Callback Result

Poll until generation reaches a terminal state:

```bash
for attempt in $(seq 1 60); do
  curl -fsS \
    -H "Authorization: Bearer $TOKEN" \
    "http://localhost:8081/api/generator/v1/generation-requests/$REQUEST_ID" \
    > /tmp/scaffoldops-generation-result.json

  STATUS="$(jq -r '.status' /tmp/scaffoldops-generation-result.json)"
  case "$STATUS" in
    GENERATED|FAILED) break ;;
  esac
  sleep 2
done

jq . /tmp/scaffoldops-generation-result.json
```

Validate the successful result:

```bash
jq -e \
  --arg imageRef "$IMAGE_REF" \
  '.status == "GENERATED"
   and (.artifactRef | startswith("file:"))
   and .imageRef == $imageRef' \
  /tmp/scaffoldops-generation-result.json
```

Expected shape:

```json
{
  "id": "<requestId>",
  "status": "GENERATED",
  "message": "generator-worker generated the project and built the Docker image",
  "artifactRef": "file:///tmp/scaffoldops-demo-worker/manifests/<serviceName>-<requestId>/",
  "imageRef": "scaffoldops/<serviceName>:<requestId>"
}
```

## 10. Optional Database Verification

```bash
docker exec scaffoldops-demo-postgres psql \
  -U generatorapiuser \
  -d generatorapidb \
  -c "SELECT id, status, message, artifact_ref, image_ref
      FROM generation_requests
      WHERE id = '$REQUEST_ID';"
```

## Troubleshooting

### Callback returns 405

The worker is still using `POST`. It must use `PATCH`.

### Callback returns 400

Check that:

- The payload uses `message`, not `detail`.
- The status is `GENERATING`, `GENERATED`, or `FAILED`.
- Transitions follow `RECEIVED -> GENERATING -> GENERATED|FAILED`.

### Callback returns 401

The worker did not send a valid service JWT. Confirm
`GENERATOR_API_BEARER_TOKEN` support and the configured issuer/audience.

### API remains GENERATING

Check worker logs for a failed Docker build. A build failure must result in a
`FAILED` callback rather than `GENERATED`.

### Kafka consumer does not receive the event

Confirm both applications use `localhost:9092`, the topic exists, and the
consumer group is running:

```bash
docker logs scaffoldops-demo-kafka --tail 100
docker exec scaffoldops-demo-kafka kafka-topics \
  --bootstrap-server scaffoldops-demo-kafka:29092 --list
```

## Cleanup

Stop both Spring Boot processes, then run:

```bash
docker rm -f scaffoldops-demo-kafka scaffoldops-demo-postgres
docker network rm scaffoldops-demo
rm -rf /tmp/scaffoldops-demo-worker
rm -f /tmp/scaffoldops-generation-request.json
rm -f /tmp/scaffoldops-generation-result.json
```
