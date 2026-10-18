package com.scaffoldops.generatorapi.domain.event;

import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeploymentRequestedEvent(
        @com.fasterxml.jackson.annotation.JsonProperty("generationRequestId") UUID requestId,
        @com.fasterxml.jackson.annotation.JsonProperty("name") String serviceName,
        @com.fasterxml.jackson.annotation.JsonIgnore DeploymentTarget deploymentTarget,
        String artifactRef,
        String imageRef,
        String namespace,
        Integer replicas,
        OffsetDateTime requestedAt
) {
}
