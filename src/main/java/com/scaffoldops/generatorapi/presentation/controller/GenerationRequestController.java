package com.scaffoldops.generatorapi.presentation.controller;

import com.scaffoldops.generatorapi.application.port.in.CreateGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.GetGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.ListGenerationRequestsUseCase;
import com.scaffoldops.generatorapi.openapi.api.GenerationRequestApi;
import com.scaffoldops.generatorapi.openapi.model.CreateGenerationRequestRequest;
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
    private final GetGenerationRequestUseCase getGenerationRequestUseCase;
    private final ListGenerationRequestsUseCase listGenerationRequestsUseCase;
    private final GenerationRequestApiMapper generationRequestApiMapper;

    public GenerationRequestController(
            CreateGenerationRequestUseCase createGenerationRequestUseCase,
            GetGenerationRequestUseCase getGenerationRequestUseCase,
            ListGenerationRequestsUseCase listGenerationRequestsUseCase,
            GenerationRequestApiMapper generationRequestApiMapper
    ) {
        this.createGenerationRequestUseCase = createGenerationRequestUseCase;
        this.getGenerationRequestUseCase = getGenerationRequestUseCase;
        this.listGenerationRequestsUseCase = listGenerationRequestsUseCase;
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
    public ResponseEntity<List<GenerationRequestResponse>> listGenerationRequests() {
        return ResponseEntity.ok(
                listGenerationRequestsUseCase.getAll().stream()
                        .map(generationRequestApiMapper::toResponse)
                        .toList()
        );
    }
}
