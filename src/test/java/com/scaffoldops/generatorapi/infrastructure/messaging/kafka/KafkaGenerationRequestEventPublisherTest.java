package com.scaffoldops.generatorapi.infrastructure.messaging.kafka;

import com.scaffoldops.generatorapi.domain.event.ArtifactCleanupRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.DeploymentRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.GenerationRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.UndeploymentRequestedEvent;
import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.GenerationStatus;
import com.scaffoldops.generatorapi.infrastructure.config.KafkaTopicProperties;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaGenerationRequestEventPublisherTest {

    @Test
    void shouldPublishGenerationRequestedToConfiguredTopic() throws Exception {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaGenerationRequestEventPublisher publisher = new KafkaGenerationRequestEventPublisher(
                kafkaTemplate,
                topics()
        );
        UUID requestId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        GenerationRequestedEvent event = new GenerationRequestedEvent(
                requestId,
                "billing-service",
                "spring-boot-hexagonal",
                true,
                true,
                true,
                false,
                DeploymentTarget.KUBERNETES,
                GenerationStatus.RECEIVED,
                OffsetDateTime.parse("2026-03-07T10:15:30Z")
        );

        publisher.publishGenerationRequested(event);

        org.mockito.ArgumentCaptor<Object> published = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq("generation-requested"),
                org.mockito.ArgumentMatchers.eq(requestId.toString()), published.capture());
        try (var serializer = new org.springframework.kafka.support.serializer.JsonSerializer<Object>();
             var fixture = getClass().getResourceAsStream("/contracts/generation-requested.json")) {
            var mapper = org.springframework.kafka.support.JacksonUtils.enhancedObjectMapper();
            org.assertj.core.api.Assertions.assertThat(mapper.readTree(
                    serializer.serialize("generation-requested", published.getValue())))
                    .isEqualTo(mapper.readTree(fixture));
        }
    }

    @Test
    void shouldPublishDeploymentRequestedToConfiguredTopic() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaGenerationRequestEventPublisher publisher = new KafkaGenerationRequestEventPublisher(kafkaTemplate, topics());
        UUID requestId = UUID.randomUUID();
        DeploymentRequestedEvent event = new DeploymentRequestedEvent(
                requestId,
                "billing-service",
                DeploymentTarget.KUBERNETES,
                "s3://artifacts/billing.zip",
                "registry/billing:latest",
                "scaffoldops-dev",
                2,
                OffsetDateTime.parse("2026-03-07T10:15:30Z")
        );

        org.mockito.Mockito.when(kafkaTemplate.send("deployment-requested", requestId.toString(), event))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        publisher.publishDeploymentRequested(event);

        verify(kafkaTemplate).send("deployment-requested", requestId.toString(), event);
    }

    @Test
    void shouldPublishUndeploymentRequestedToConfiguredTopic() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaGenerationRequestEventPublisher publisher = new KafkaGenerationRequestEventPublisher(kafkaTemplate, topics());
        UUID requestId = UUID.randomUUID();
        UndeploymentRequestedEvent event = new UndeploymentRequestedEvent(
                requestId,
                "billing-service",
                DeploymentTarget.KUBERNETES,
                "s3://artifacts/billing.zip",
                "registry/billing:latest",
                "scaffoldops-dev",
                OffsetDateTime.parse("2026-03-07T10:15:30Z")
        );

        org.mockito.Mockito.when(kafkaTemplate.send("undeployment-requested", requestId.toString(), event))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        publisher.publishUndeploymentRequested(event);

        verify(kafkaTemplate).send("undeployment-requested", requestId.toString(), event);
    }

    @Test
    void shouldPublishArtifactCleanupRequestedToConfiguredTopic() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaGenerationRequestEventPublisher publisher = new KafkaGenerationRequestEventPublisher(
                kafkaTemplate,
                topics()
        );
        UUID requestId = UUID.randomUUID();
        ArtifactCleanupRequestedEvent event = new ArtifactCleanupRequestedEvent(
                requestId,
                "billing-service",
                OffsetDateTime.parse("2026-03-07T10:15:30Z")
        );

        org.mockito.Mockito.when(kafkaTemplate.send("artifact-cleanup-requested", requestId.toString(), event))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        publisher.publishArtifactCleanupRequested(event);

        verify(kafkaTemplate).send("artifact-cleanup-requested", requestId.toString(), event);
    }

    @Test
    void shouldPropagateCleanupPublicationFailure() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        var publisher = new KafkaGenerationRequestEventPublisher(kafkaTemplate, topics());
        var event = new ArtifactCleanupRequestedEvent(UUID.randomUUID(), "billing", OffsetDateTime.now());
        org.mockito.Mockito.when(kafkaTemplate.send("artifact-cleanup-requested", event.requestId().toString(), event))
                .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> publisher.publishArtifactCleanupRequested(event))
                .isInstanceOf(IllegalStateException.class).hasMessage("cleanup publication was not acknowledged");
    }

    @Test
    void deploymentJsonUsesSharedMvpContract() throws Exception {
        var json = org.springframework.kafka.support.JacksonUtils.enhancedObjectMapper();
        UUID id = UUID.randomUUID();
        var deploy = json.readTree(json.writeValueAsString(new DeploymentRequestedEvent(id, "hello", DeploymentTarget.KUBERNETES,
                "s3://a/b", "docker.io/a/b:tag", "generated-dev", 1, OffsetDateTime.now())));
        org.assertj.core.api.Assertions.assertThat(deploy.size()).isEqualTo(7);
        org.assertj.core.api.Assertions.assertThat(deploy.path("generationRequestId").asText()).isEqualTo(id.toString());
        org.assertj.core.api.Assertions.assertThat(deploy.path("name").asText()).isEqualTo("hello");
        org.assertj.core.api.Assertions.assertThat(deploy.path("artifactRef").asText()).isEqualTo("s3://a/b");
        org.assertj.core.api.Assertions.assertThat(deploy.path("imageRef").asText()).isEqualTo("docker.io/a/b:tag");
        org.assertj.core.api.Assertions.assertThat(deploy.path("replicas").asInt()).isEqualTo(1);
        var undeploy = json.readTree(json.writeValueAsString(new UndeploymentRequestedEvent(id, "hello", DeploymentTarget.KUBERNETES,
                "s3://a/b", "docker.io/a/b:tag", "generated-dev", OffsetDateTime.now())));
        org.assertj.core.api.Assertions.assertThat(undeploy.size()).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(undeploy.path("namespace").asText()).isEqualTo("generated-dev");
    }

    @Test
    void propagatesDeploymentBrokerFailure() {
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        var publisher = new KafkaGenerationRequestEventPublisher(kafka, topics());
        var event = new DeploymentRequestedEvent(UUID.randomUUID(), "hello", DeploymentTarget.KUBERNETES,
                "s3://a/b", "image", "generated-dev", 1, OffsetDateTime.now());
        org.mockito.Mockito.when(kafka.send("deployment-requested", event.requestId().toString(), event))
                .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> publisher.publishDeploymentRequested(event))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not acknowledged");
    }

    private KafkaTopicProperties topics() {
        return new KafkaTopicProperties(
                "generation-requested",
                "deployment-requested",
                "undeployment-requested",
                "artifact-cleanup-requested"
        );
    }
}
