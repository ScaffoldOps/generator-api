package com.scaffoldops.generatorapi.application.port.in;

import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;

public interface CreateGenerationRequestUseCase {

    GenerationRequest create(Command command);

    record Command(
            String name,
            String template,
            boolean database,
            boolean restApi,
            boolean security,
            boolean messaging,
            DeploymentTarget deploymentTarget,
            String specJson
    ) {
    }
}
