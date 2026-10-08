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
        GenerationStatus generationStatus,
        DeploymentStatus deploymentStatus,
        String specJson,
        String message,
        String artifactRef,
        String imageRef,
        String deploymentNamespace,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String failureStage,
        Integer retryCount
) {
    public GenerationRequest(UUID id, String name, String template, boolean database,
            boolean restApi, boolean security, boolean messaging, DeploymentTarget deploymentTarget,
            GenerationStatus generationStatus, DeploymentStatus deploymentStatus, String specJson,
            String message, String artifactRef, String imageRef, String deploymentNamespace,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this(id, name, template, database, restApi, security, messaging, deploymentTarget,
                generationStatus, deploymentStatus, specJson, message, artifactRef, imageRef,
                deploymentNamespace, createdAt, updatedAt, null, 0);
    }
}
