package com.scaffoldops.generatorapi.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GenerationRequest(
        UUID id,
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
