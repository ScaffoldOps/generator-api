package com.scaffoldops.generatorapi.application.port.out;

import com.scaffoldops.generatorapi.domain.event.ArtifactCleanupRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.DeploymentRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.GenerationRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.UndeploymentRequestedEvent;

public interface GenerationRequestEventPublisher {

    void publishGenerationRequested(GenerationRequestedEvent event);

    void publishDeploymentRequested(DeploymentRequestedEvent event);

    void publishUndeploymentRequested(UndeploymentRequestedEvent event);

    void publishArtifactCleanupRequested(ArtifactCleanupRequestedEvent event);
}
