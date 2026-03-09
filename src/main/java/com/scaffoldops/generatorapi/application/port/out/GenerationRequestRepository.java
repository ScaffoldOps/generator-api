package com.scaffoldops.generatorapi.application.port.out;

import com.scaffoldops.generatorapi.domain.model.GenerationRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GenerationRequestRepository {

    GenerationRequest save(GenerationRequest generationRequest);

    Optional<GenerationRequest> findById(UUID id);

    List<GenerationRequest> findAll();
}
