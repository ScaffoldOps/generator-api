package com.scaffoldops.generatorapi.application.service;

import com.scaffoldops.generatorapi.application.port.in.CreateGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.GetGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.out.GenerationRequestEventPublisher;
import com.scaffoldops.generatorapi.application.port.in.ListGenerationRequestsUseCase;
import com.scaffoldops.generatorapi.application.port.out.GenerationRequestRepository;
import com.scaffoldops.generatorapi.domain.event.GenerationRequestedEvent;
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
    private final GenerationRequestEventPublisher generationRequestEventPublisher;

    public GenerationRequestService(
            GenerationRequestRepository generationRequestRepository,
            GenerationRequestEventPublisher generationRequestEventPublisher
    ) {
        this.generationRequestRepository = generationRequestRepository;
        this.generationRequestEventPublisher = generationRequestEventPublisher;
    }

    @Override
    public GenerationRequest create(Command command) {
        OffsetDateTime now = OffsetDateTime.now();
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
                command.specJson(),
                now,
                now
        );

        GenerationRequest savedRequest = generationRequestRepository.save(generationRequest);
        generationRequestEventPublisher.publishGenerationRequested(new GenerationRequestedEvent(
                savedRequest.id(),
                savedRequest.name(),
                savedRequest.template(),
                savedRequest.database(),
                savedRequest.restApi(),
                savedRequest.security(),
                savedRequest.messaging(),
                savedRequest.deploymentTarget(),
                savedRequest.status(),
                savedRequest.createdAt()
        ));

        return savedRequest;
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
