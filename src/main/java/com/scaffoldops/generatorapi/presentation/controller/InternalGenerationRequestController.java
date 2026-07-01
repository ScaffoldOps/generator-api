package com.scaffoldops.generatorapi.presentation.controller;

import com.scaffoldops.generatorapi.application.port.in.UpdateGenerationRequestStatusUseCase;
import com.scaffoldops.generatorapi.openapi.api.InternalGenerationRequestApi;
import com.scaffoldops.generatorapi.openapi.model.GenerationStatusUpdateRequest;
import com.scaffoldops.generatorapi.presentation.mapper.GenerationRequestApiMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
public class InternalGenerationRequestController implements InternalGenerationRequestApi {

    private final UpdateGenerationRequestStatusUseCase updateGenerationRequestStatusUseCase;
    private final GenerationRequestApiMapper generationRequestApiMapper;

    public InternalGenerationRequestController(
            UpdateGenerationRequestStatusUseCase updateGenerationRequestStatusUseCase,
            GenerationRequestApiMapper generationRequestApiMapper
    ) {
        this.updateGenerationRequestStatusUseCase = updateGenerationRequestStatusUseCase;
        this.generationRequestApiMapper = generationRequestApiMapper;
    }

    @Override
    public ResponseEntity<Void> updateGenerationRequestStatus(
            UUID requestId,
            GenerationStatusUpdateRequest request
    ) {
        UpdateGenerationRequestStatusUseCase.Result result = updateGenerationRequestStatusUseCase.updateStatus(
                requestId,
                generationRequestApiMapper.toCommand(request)
        );

        return switch (result) {
            case UPDATED -> ResponseEntity.noContent().build();
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Generation request not found"
            );
            case INVALID_TRANSITION -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid generation request status transition"
            );
        };
    }
}
