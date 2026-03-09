package com.scaffoldops.generatorapi.presentation.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffoldops.generatorapi.application.port.in.CreateGenerationRequestUseCase;
import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.openapi.model.CreateGenerationRequestRequest;
import com.scaffoldops.generatorapi.openapi.model.GenerationRequestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

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
                .createdAt(generationRequest.createdAt())
                .updatedAt(generationRequest.updatedAt());
    }

    private String toSpecJson(CreateGenerationRequestRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize generation request");
        }
    }
}
