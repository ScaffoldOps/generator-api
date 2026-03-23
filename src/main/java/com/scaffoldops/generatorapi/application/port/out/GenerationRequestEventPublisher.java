package com.scaffoldops.generatorapi.application.port.out;

import com.scaffoldops.generatorapi.domain.event.GenerationRequestedEvent;

public interface GenerationRequestEventPublisher {

    void publishGenerationRequested(GenerationRequestedEvent event);
}
