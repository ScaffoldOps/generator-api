package com.scaffoldops.generatorapi.presentation.mapper;

import com.scaffoldops.generatorapi.application.port.in.CreateGenerationRequestUseCase;
import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.openapi.model.CreateGenerationRequestRequest;
import com.scaffoldops.generatorapi.openapi.model.GenerationRequestResponse;
import org.springframework.stereotype.Component;

@Component
public class GenerationRequestApiMapper {

    public CreateGenerationRequestUseCase.Command toCommand(CreateGenerationRequestRequest request) {
        return new CreateGenerationRequestUseCase.Command(
                request.getName(),
                request.getTemplate(),
                Boolean.TRUE.equals(request.getDatabase()),
                Boolean.TRUE.equals(request.getRestApi()),
                Boolean.TRUE.equals(request.getSecurity()),
                Boolean.TRUE.equals(request.getMessaging()),
                DeploymentTarget.valueOf(request.getDeploymentTarget().getValue())
        );
    }

    public GenerationRequestResponse toResponse(GenerationRequest generationRequest) {
        return new GenerationRequestResponse()
                .id(generationRequest.id())
                .name(generationRequest.name())
                .template(generationRequest.template())
                .database(generationRequest.database())
                .restApi(generationRequest.restApi())
                .security(generationRequest.security())
                .messaging(generationRequest.messaging())
                .deploymentTarget(com.scaffoldops.generatorapi.openapi.model.DeploymentTarget.fromValue(
                        generationRequest.deploymentTarget().name()
                ))
                .status(com.scaffoldops.generatorapi.openapi.model.GenerationRequestStatus.fromValue(
                        generationRequest.status().name()
                ))
                .createdAt(generationRequest.createdAt());
    }
}
