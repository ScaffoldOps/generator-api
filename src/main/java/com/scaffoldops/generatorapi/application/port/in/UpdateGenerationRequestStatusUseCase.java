package com.scaffoldops.generatorapi.application.port.in;

import com.scaffoldops.generatorapi.domain.model.GenerationStatus;

import java.util.UUID;

public interface UpdateGenerationRequestStatusUseCase {

    Result updateGenerationStatus(UUID requestId, Command command);

    record Command(
            GenerationStatus generationStatus,
            String message,
            String artifactRef,
            String imageRef,
            String failureStage,
            Integer retryCount
    ) {
        public Command(GenerationStatus generationStatus, String message, String artifactRef, String imageRef) {
            this(generationStatus, message, artifactRef, imageRef, null, null);
        }
    }

    enum Result {
        UPDATED,
        NOT_FOUND,
        INVALID_REFERENCES,
        INVALID_TRANSITION
    }
}
