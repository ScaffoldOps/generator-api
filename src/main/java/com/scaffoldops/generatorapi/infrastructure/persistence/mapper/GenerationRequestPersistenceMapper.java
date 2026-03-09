package com.scaffoldops.generatorapi.infrastructure.persistence.mapper;

import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.infrastructure.persistence.entity.GenerationRequestEntity;
import org.springframework.stereotype.Component;

@Component
public class GenerationRequestPersistenceMapper {

    public GenerationRequestEntity toEntity(GenerationRequest generationRequest) {
        return new GenerationRequestEntity(
                generationRequest.id(),
                generationRequest.name(),
                generationRequest.template(),
                generationRequest.database(),
                generationRequest.restApi(),
                generationRequest.security(),
                generationRequest.messaging(),
                generationRequest.deploymentTarget(),
                generationRequest.status(),
                generationRequest.specJson(),
                generationRequest.createdAt(),
                generationRequest.updatedAt()
        );
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
                entity.getStatus(),
                entity.getSpecJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
