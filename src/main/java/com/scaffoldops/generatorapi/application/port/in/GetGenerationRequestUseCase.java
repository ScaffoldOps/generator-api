package com.scaffoldops.generatorapi.application.port.in;

import com.scaffoldops.generatorapi.domain.model.GenerationRequest;

import java.util.Optional;
import java.util.UUID;

public interface GetGenerationRequestUseCase {

    Optional<GenerationRequest> getById(UUID id);
}
