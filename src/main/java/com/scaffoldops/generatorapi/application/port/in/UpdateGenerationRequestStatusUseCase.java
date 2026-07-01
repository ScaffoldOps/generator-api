package com.scaffoldops.generatorapi.application.port.in;

import com.scaffoldops.generatorapi.domain.model.GenerationRequestStatus;

import java.util.UUID;

public interface UpdateGenerationRequestStatusUseCase {

    Result updateStatus(UUID requestId, Command command);

    record Command(
            GenerationRequestStatus status,
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
