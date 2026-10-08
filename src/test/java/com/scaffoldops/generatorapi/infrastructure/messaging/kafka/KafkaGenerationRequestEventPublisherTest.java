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

        publisher.publishArtifactCleanupRequested(event);

        verify(kafkaTemplate).send("artifact-cleanup-requested", requestId.toString(), event);
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
