package com.scaffoldops.generatorapi.infrastructure.messaging.kafka;

import com.scaffoldops.generatorapi.application.port.out.GenerationRequestEventPublisher;
import com.scaffoldops.generatorapi.domain.event.GenerationRequestedEvent;
import com.scaffoldops.generatorapi.infrastructure.config.KafkaTopicProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaGenerationRequestEventPublisher implements GenerationRequestEventPublisher {

    private final KafkaTemplate<String, GenerationRequestedEvent> kafkaTemplate;
    private final KafkaTopicProperties kafkaTopicProperties;

    public KafkaGenerationRequestEventPublisher(
            KafkaTemplate<String, GenerationRequestedEvent> kafkaTemplate,
            KafkaTopicProperties kafkaTopicProperties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopicProperties = kafkaTopicProperties;
    }

    @Override
    public void publishGenerationRequested(GenerationRequestedEvent event) {
        kafkaTemplate.send(kafkaTopicProperties.generationRequested(), event.requestId().toString(), event);
    }
}
