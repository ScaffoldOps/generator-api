package com.scaffoldops.generatorapi.application.port.in;

import com.scaffoldops.generatorapi.domain.model.GenerationStatus;

import java.util.UUID;

public interface UpdateGenerationRequestStatusUseCase {

    Result updateGenerationStatus(UUID requestId, Command command);

    record Command(
            GenerationStatus generationStatus,
            String message,
            String artifactRef,
            String imageRef
    ) {
    }

    enum Result {
        UPDATED,
        NOT_FOUND,
        INVALID_TRANSITION
    }
}
