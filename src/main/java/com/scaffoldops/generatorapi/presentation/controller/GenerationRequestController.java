package com.scaffoldops.generatorapi.presentation.controller;

import com.scaffoldops.generatorapi.application.port.in.CreateGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.DeleteGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.GetGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.ListGenerationRequestsUseCase;
import com.scaffoldops.generatorapi.application.port.in.RequestDeploymentUseCase;
import com.scaffoldops.generatorapi.application.port.in.RequestUndeploymentUseCase;
import com.scaffoldops.generatorapi.openapi.api.GenerationRequestApi;
import com.scaffoldops.generatorapi.openapi.model.CreateGenerationRequestRequest;
import com.scaffoldops.generatorapi.openapi.model.DeployGenerationRequestRequest;
import com.scaffoldops.generatorapi.openapi.model.GenerationRequestResponse;
import com.scaffoldops.generatorapi.presentation.mapper.GenerationRequestApiMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
public class GenerationRequestController implements GenerationRequestApi {

    private final CreateGenerationRequestUseCase createGenerationRequestUseCase;
    private final DeleteGenerationRequestUseCase deleteGenerationRequestUseCase;
    private final GetGenerationRequestUseCase getGenerationRequestUseCase;
    private final ListGenerationRequestsUseCase listGenerationRequestsUseCase;
    private final RequestDeploymentUseCase requestDeploymentUseCase;
    private final RequestUndeploymentUseCase requestUndeploymentUseCase;
    private final GenerationRequestApiMapper generationRequestApiMapper;

    public GenerationRequestController(
            CreateGenerationRequestUseCase createGenerationRequestUseCase,
            DeleteGenerationRequestUseCase deleteGenerationRequestUseCase,
            GetGenerationRequestUseCase getGenerationRequestUseCase,
            ListGenerationRequestsUseCase listGenerationRequestsUseCase,
            RequestDeploymentUseCase requestDeploymentUseCase,
            RequestUndeploymentUseCase requestUndeploymentUseCase,
            GenerationRequestApiMapper generationRequestApiMapper
    ) {
        this.createGenerationRequestUseCase = createGenerationRequestUseCase;
        this.deleteGenerationRequestUseCase = deleteGenerationRequestUseCase;
        this.getGenerationRequestUseCase = getGenerationRequestUseCase;
        this.listGenerationRequestsUseCase = listGenerationRequestsUseCase;
        this.requestDeploymentUseCase = requestDeploymentUseCase;
        this.requestUndeploymentUseCase = requestUndeploymentUseCase;
        this.generationRequestApiMapper = generationRequestApiMapper;
    }

    @Override
    public ResponseEntity<GenerationRequestResponse> createGenerationRequest(CreateGenerationRequestRequest request) {
        GenerationRequestResponse response = generationRequestApiMapper.toResponse(
                createGenerationRequestUseCase.create(generationRequestApiMapper.toCommand(request))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<GenerationRequestResponse> getGenerationRequestById(UUID id) {
        return getGenerationRequestUseCase.getById(id)
                .map(generationRequestApiMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Generation request not found"));
    }

    @Override
    public ResponseEntity<Void> deleteGenerationRequestById(UUID id) {
        if (!deleteGenerationRequestUseCase.deleteById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Generation request not found");
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deployGenerationRequest(UUID id, DeployGenerationRequestRequest request) {
        RequestDeploymentUseCase.Result result = requestDeploymentUseCase.requestDeployment(
                generationRequestApiMapper.toCommand(id, request)
        );
        return switch (result) {
            case ACCEPTED -> ResponseEntity.accepted().build();
            case NOT_FOUND -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Generation request not found");
            case INVALID_TRANSITION -> throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Deployment requires GENERATED with nonblank artifactRef and imageRef, an eligible deployment state, and a valid namespace and replica count"
            );
        };
    }

    @Override
    public ResponseEntity<Void> undeployGenerationRequest(UUID id) {
        RequestUndeploymentUseCase.Result result = requestUndeploymentUseCase.requestUndeployment(id);
        return switch (result) {
            case ACCEPTED -> ResponseEntity.accepted().build();
            case NOT_FOUND -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Generation request not found");
            case INVALID_TRANSITION -> throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Generation request cannot be undeployed from its current lifecycle state"
            );
        };
    }

    @Override
    public ResponseEntity<List<GenerationRequestResponse>> listGenerationRequests(
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
        return ResponseEntity.ok(
                listGenerationRequestsUseCase.getAll(generationRequestApiMapper.toFilters(
                                name,
                                template,
                                generationStatus,
                                deploymentStatus,
                                deploymentTarget,
                                database,
                                restApi,
                                security,
                                messaging
                        )).stream()
                        .map(generationRequestApiMapper::toResponse)
                        .toList()
        );
    }
}
