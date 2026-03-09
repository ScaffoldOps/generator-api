package com.scaffoldops.generatorapi.domain.event;

import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.GenerationRequestStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GenerationRequestedEvent(
        UUID requestId,
        String name,
        String template,
        boolean database,
        boolean restApi,
        boolean security,
        boolean messaging,
        DeploymentTarget deploymentTarget,
        GenerationRequestStatus status,
        OffsetDateTime createdAt
) {
}
