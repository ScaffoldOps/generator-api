package com.scaffoldops.generatorapi.application.model;

import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.GenerationRequestStatus;

public record GenerationRequestFilters(
        String name,
        String template,
        GenerationRequestStatus status,
        DeploymentTarget deploymentTarget,
        Boolean database,
        Boolean restApi,
        Boolean security,
        Boolean messaging
) {

    public static GenerationRequestFilters empty() {
        return new GenerationRequestFilters(null, null, null, null, null, null, null, null);
    }
}
