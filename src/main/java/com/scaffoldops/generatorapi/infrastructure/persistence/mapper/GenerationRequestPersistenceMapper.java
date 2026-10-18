package com.scaffoldops.generatorapi.infrastructure.persistence.mapper;

import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.infrastructure.persistence.entity.GenerationRequestEntity;
import org.springframework.stereotype.Component;

@Component
public class GenerationRequestPersistenceMapper {

    public GenerationRequestEntity toEntity(GenerationRequest generationRequest) {
        GenerationRequestEntity entity = new GenerationRequestEntity(
                generationRequest.id(),
                generationRequest.name(),
                generationRequest.template(),
                generationRequest.database(),
                generationRequest.restApi(),
                generationRequest.security(),
                generationRequest.messaging(),
                generationRequest.deploymentTarget(),
                generationRequest.generationStatus(),
                generationRequest.deploymentStatus(),
                generationRequest.specJson(),
                generationRequest.message(),
                generationRequest.artifactRef(),
                generationRequest.imageRef(),
                generationRequest.deploymentNamespace(),
                generationRequest.createdAt(),
                generationRequest.updatedAt()
        );
        entity.setFailureStage(generationRequest.failureStage());
        entity.setRetryCount(generationRequest.retryCount());
        return entity;
    }

    public GenerationRequest toDomain(GenerationRequestEntity entity) {
        return new GenerationRequest(
                entity.getId(),
                entity.getName(),
                entity.getTemplate(),
                entity.isDatabase(),
                entity.isRestApi(),
                entity.isSecurity(),
                entity.isMessaging(),
                entity.getDeploymentTarget(),
                entity.getGenerationStatus(),
                entity.getDeploymentStatus(),
                entity.getSpecJson(),
                entity.getMessage(),
                entity.getArtifactRef(),
                entity.getImageRef(),
                entity.getDeploymentNamespace(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getFailureStage(),
                entity.getRetryCount()
        );
    }
}
