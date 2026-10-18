package com.scaffoldops.generatorapi.infrastructure.messaging.kafka;

import com.scaffoldops.generatorapi.application.port.out.GenerationRequestEventPublisher;
import com.scaffoldops.generatorapi.domain.event.ArtifactCleanupRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.DeploymentRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.GenerationRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.UndeploymentRequestedEvent;
import com.scaffoldops.generatorapi.infrastructure.config.KafkaTopicProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaGenerationRequestEventPublisher implements GenerationRequestEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties kafkaTopicProperties;

    public KafkaGenerationRequestEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaTopicProperties kafkaTopicProperties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopicProperties = kafkaTopicProperties;
    }

    @Override
    public void publishGenerationRequested(GenerationRequestedEvent event) {
        kafkaTemplate.send(kafkaTopicProperties.generationRequested(), event.requestId().toString(), event);
    }

    @Override
    public void publishDeploymentRequested(DeploymentRequestedEvent event) {
        publishAcknowledged(kafkaTopicProperties.deploymentRequested(), event.requestId().toString(), event);
    }

    @Override
    public void publishUndeploymentRequested(UndeploymentRequestedEvent event) {
        publishAcknowledged(kafkaTopicProperties.undeploymentRequested(), event.requestId().toString(), event);
    }

    private void publishAcknowledged(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event).get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Deployment publication interrupted", ex);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
            throw new IllegalStateException("Deployment publication was not acknowledged", ex);
        }
    }

    @Override
    public void publishArtifactCleanupRequested(ArtifactCleanupRequestedEvent event) {
        try {
            // Broker acknowledgement precedes the DB commit; failure rolls back the deletion.
            kafkaTemplate.send(kafkaTopicProperties.artifactCleanupRequested(), event.requestId().toString(), event)
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("cleanup publication interrupted", exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("cleanup publication was not acknowledged", exception);
        }
    }
}
