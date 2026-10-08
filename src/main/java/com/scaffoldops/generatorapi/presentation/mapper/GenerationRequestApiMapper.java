package com.scaffoldops.generatorapi.presentation.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffoldops.generatorapi.application.model.GenerationRequestFilters;
import com.scaffoldops.generatorapi.application.port.in.CreateGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.RequestDeploymentUseCase;
import com.scaffoldops.generatorapi.application.port.in.UpdateDeploymentRequestStatusUseCase;
import com.scaffoldops.generatorapi.application.port.in.UpdateGenerationRequestStatusUseCase;
import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.DeploymentStatus;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.domain.model.GenerationStatus;
import com.scaffoldops.generatorapi.openapi.model.CreateGenerationRequestRequest;
import com.scaffoldops.generatorapi.openapi.model.DeployGenerationRequestRequest;
import com.scaffoldops.generatorapi.openapi.model.DeploymentStatusUpdateRequest;
import com.scaffoldops.generatorapi.openapi.model.GenerationRequestFeaturesResponse;
import com.scaffoldops.generatorapi.openapi.model.GenerationRequestGenerationResponse;
import com.scaffoldops.generatorapi.openapi.model.GenerationRequestDeploymentResponse;
import com.scaffoldops.generatorapi.openapi.model.GenerationRequestTimestampsResponse;
import com.scaffoldops.generatorapi.openapi.model.GenerationRequestResponse;
import com.scaffoldops.generatorapi.openapi.model.GenerationStatusUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class GenerationRequestApiMapper {

    private final ObjectMapper objectMapper;

    public GenerationRequestApiMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CreateGenerationRequestUseCase.Command toCommand(CreateGenerationRequestRequest request) {
        return new CreateGenerationRequestUseCase.Command(
                request.getName(),
                request.getTemplate(),
                Boolean.TRUE.equals(request.getDatabase()),
                Boolean.TRUE.equals(request.getRestApi()),
                Boolean.TRUE.equals(request.getSecurity()),
                Boolean.TRUE.equals(request.getMessaging()),
                DeploymentTarget.valueOf(request.getDeploymentTarget().getValue()),
                toSpecJson(request)
        );
    }

    public RequestDeploymentUseCase.Command toCommand(UUID requestId, DeployGenerationRequestRequest request) {
        return new RequestDeploymentUseCase.Command(
                requestId,
                request.getNamespace(),
                request.getReplicas()
        );
    }

    public GenerationRequestResponse toResponse(GenerationRequest generationRequest) {
        return new GenerationRequestResponse()
                .id(generationRequest.id())
                .name(generationRequest.name())
                .template(generationRequest.template())
                .deploymentTarget(com.scaffoldops.generatorapi.openapi.model.DeploymentTarget.fromValue(
                        generationRequest.deploymentTarget().name()
                ))
                .features(new GenerationRequestFeaturesResponse()
                        .database(generationRequest.database())
                        .restApi(generationRequest.restApi())
                        .security(generationRequest.security())
                        .messaging(generationRequest.messaging()))
                .generation(new GenerationRequestGenerationResponse()
                        .status(com.scaffoldops.generatorapi.openapi.model.GenerationStatus.fromValue(
                                generationRequest.generationStatus().name()))
                        .message(generationRequest.message())
                        .artifactRef(generationRequest.artifactRef())
                        .imageRef(generationRequest.imageRef())
                        .failureStage(generationRequest.failureStage())
                        .retryCount(generationRequest.retryCount()))
                .deployment(new GenerationRequestDeploymentResponse()
                        .status(com.scaffoldops.generatorapi.openapi.model.DeploymentStatus.fromValue(
                                generationRequest.deploymentStatus().name()))
                        .namespace(generationRequest.deploymentNamespace()))
                .timestamps(new GenerationRequestTimestampsResponse()
                        .createdAt(generationRequest.createdAt())
                        .updatedAt(generationRequest.updatedAt()));
    }

    public UpdateGenerationRequestStatusUseCase.Command toCommand(GenerationStatusUpdateRequest request) {
        return new UpdateGenerationRequestStatusUseCase.Command(
                GenerationStatus.valueOf(request.getGenerationStatus().getValue()),
                request.getMessage(),
                request.getArtifactRef(),
                request.getImageRef(),
                request.getFailureStage(),
                request.getRetryCount()
        );
    }

    public UpdateDeploymentRequestStatusUseCase.Command toCommand(DeploymentStatusUpdateRequest request) {
        return new UpdateDeploymentRequestStatusUseCase.Command(
                DeploymentStatus.valueOf(request.getDeploymentStatus().getValue()),
                request.getMessage(),
                request.getImageRef(),
                request.getNamespace()
        );
    }

    public GenerationRequestFilters toFilters(
            String name,
            String template,
            com.scaffoldops.generatorapi.openapi.model.GenerationStatus generationStatus,
            com.scaffoldops.generatorapi.openapi.model.DeploymentStatus deploymentStatus,
            com.scaffoldops.generatorapi.openapi.model.DeploymentTarget deploymentTarget,
            Boolean database,
            Boolean restApi,
            Boolean security,
            Boolean messaging
    ) {
        return new GenerationRequestFilters(
                name,
                template,
                generationStatus == null ? null : GenerationStatus.valueOf(generationStatus.getValue()),
                deploymentStatus == null ? null : DeploymentStatus.valueOf(deploymentStatus.getValue()),
                deploymentTarget == null ? null : DeploymentTarget.valueOf(deploymentTarget.getValue()),
                database,
                restApi,
                security,
                messaging
        );
    }

    private String toSpecJson(CreateGenerationRequestRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize generation request");
        }
    }
}
