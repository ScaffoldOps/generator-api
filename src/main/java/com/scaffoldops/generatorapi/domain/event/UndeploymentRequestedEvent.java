package com.scaffoldops.generatorapi.domain.event;

import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UndeploymentRequestedEvent(
        @com.fasterxml.jackson.annotation.JsonProperty("generationRequestId") UUID requestId,
        @com.fasterxml.jackson.annotation.JsonProperty("name") String serviceName,
        @com.fasterxml.jackson.annotation.JsonIgnore DeploymentTarget deploymentTarget,
        @com.fasterxml.jackson.annotation.JsonIgnore String artifactRef,
        @com.fasterxml.jackson.annotation.JsonIgnore String imageRef,
        String namespace,
        OffsetDateTime requestedAt
) {
}
