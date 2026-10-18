package com.scaffoldops.generatorapi.application.port.in;

import java.util.UUID;

public interface RequestUndeploymentUseCase {

    Result requestUndeployment(UUID requestId);

    enum Result {
        ACCEPTED,
        NOT_FOUND,
        INVALID_TRANSITION
    }
}
