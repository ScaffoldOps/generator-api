package com.scaffoldops.generatorapi.application.port.in;

import java.util.UUID;

public interface RequestDeploymentUseCase {

    Result requestDeployment(UUID requestId);

    enum Result {
        ACCEPTED,
        NOT_FOUND,
        INVALID_TRANSITION
    }
}
