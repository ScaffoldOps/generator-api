package com.scaffoldops.generatorapi.application.port.in;

import java.util.UUID;

public interface RequestDeploymentUseCase {

    Result requestDeployment(Command command);

    record Command(
            UUID requestId,
            String namespace,
            Integer replicas
    ) {
    }

    enum Result {
        ACCEPTED,
        NOT_FOUND,
        INVALID_TRANSITION
    }
}
