package com.scaffoldops.generatorapi.domain.event;

import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UndeploymentRequestedEvent(
        UUID requestId,
        String serviceName,
        DeploymentTarget deploymentTarget,
        String artifactRef,
        String imageRef,
        String namespace,
        OffsetDateTime requestedAt
) {
}
