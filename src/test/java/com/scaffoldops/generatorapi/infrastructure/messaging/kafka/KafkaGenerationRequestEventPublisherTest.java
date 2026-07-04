package com.scaffoldops.generatorapi.infrastructure.messaging.kafka;

import com.scaffoldops.generatorapi.domain.event.ArtifactCleanupRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.GenerationRequestedEvent;
import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.GenerationRequestStatus;
import com.scaffoldops.generatorapi.infrastructure.config.KafkaTopicProperties;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaGenerationRequestEventPublisherTest {

    @Test
    void shouldPublishGenerationRequestedToConfiguredTopic() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaGenerationRequestEventPublisher publisher = new KafkaGenerationRequestEventPublisher(
                kafkaTemplate,
                new KafkaTopicProperties("generation-requested", "artifact-cleanup-requested")
        );
        UUID requestId = UUID.randomUUID();
        GenerationRequestedEvent event = new GenerationRequestedEvent(
                requestId,
                "billing-service",
                "spring-boot-hexagonal",
                true,
                true,
                true,
                false,
                DeploymentTarget.KUBERNETES,
                GenerationRequestStatus.RECEIVED,
                OffsetDateTime.parse("2026-03-07T10:15:30Z")
        );

        publisher.publishGenerationRequested(event);

        verify(kafkaTemplate).send("generation-requested", requestId.toString(), event);
    }

    @Test
    void shouldPublishArtifactCleanupRequestedToConfiguredTopic() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaGenerationRequestEventPublisher publisher = new KafkaGenerationRequestEventPublisher(
                kafkaTemplate,
                new KafkaTopicProperties("generation-requested", "artifact-cleanup-requested")
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
}
