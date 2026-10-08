package com.scaffoldops.generatorapi.application.port.in;

import com.scaffoldops.generatorapi.domain.model.DeploymentStatus;

import java.util.UUID;

public interface UpdateDeploymentRequestStatusUseCase {

    Result updateDeploymentStatus(UUID requestId, Command command);

    record Command(
            DeploymentStatus deploymentStatus,
            String message,
            String imageRef
    ) {
    }

    enum Result {
        UPDATED,
        NOT_FOUND,
        INVALID_TRANSITION
    }
}
