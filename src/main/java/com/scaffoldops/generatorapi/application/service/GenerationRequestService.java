package com.scaffoldops.generatorapi.application.service;

import com.scaffoldops.generatorapi.application.port.in.CreateGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.GetGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.ListGenerationRequestsUseCase;
import com.scaffoldops.generatorapi.application.port.out.GenerationRequestRepository;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.domain.model.GenerationRequestStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class GenerationRequestService implements CreateGenerationRequestUseCase, GetGenerationRequestUseCase, ListGenerationRequestsUseCase {

    private final GenerationRequestRepository generationRequestRepository;

    public GenerationRequestService(GenerationRequestRepository generationRequestRepository) {
        this.generationRequestRepository = generationRequestRepository;
    }

    @Override
    public GenerationRequest create(Command command) {
        GenerationRequest generationRequest = new GenerationRequest(
                UUID.randomUUID(),
                command.name(),
                command.template(),
                command.database(),
                command.restApi(),
                command.security(),
                command.messaging(),
                command.deploymentTarget(),
                GenerationRequestStatus.RECEIVED,
                OffsetDateTime.now()
        );

        return generationRequestRepository.save(generationRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GenerationRequest> getById(UUID id) {
        return generationRequestRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GenerationRequest> getAll() {
        return generationRequestRepository.findAll();
    }
}
