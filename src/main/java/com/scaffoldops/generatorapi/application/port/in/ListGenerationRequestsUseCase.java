package com.scaffoldops.generatorapi.application.port.in;

import com.scaffoldops.generatorapi.application.model.GenerationRequestFilters;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;

import java.util.List;

public interface ListGenerationRequestsUseCase {

    List<GenerationRequest> getAll(GenerationRequestFilters filters);
}
