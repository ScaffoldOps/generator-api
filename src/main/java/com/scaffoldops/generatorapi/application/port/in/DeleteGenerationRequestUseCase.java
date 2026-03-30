package com.scaffoldops.generatorapi.application.port.in;

import java.util.UUID;

public interface DeleteGenerationRequestUseCase {

    boolean deleteById(UUID id);
}
