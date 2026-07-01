package com.scaffoldops.generatorapi.application.service;

import com.scaffoldops.generatorapi.application.model.GenerationRequestFilters;
import com.scaffoldops.generatorapi.application.port.in.CreateGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.DeleteGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.GetGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.ListGenerationRequestsUseCase;
import com.scaffoldops.generatorapi.application.port.in.UpdateGenerationRequestStatusUseCase;
import com.scaffoldops.generatorapi.application.port.out.GenerationRequestEventPublisher;
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
public class GenerationRequestService implements CreateGenerationRequestUseCase, DeleteGenerationRequestUseCase,
        GetGenerationRequestUseCase, ListGenerationRequestsUseCase, UpdateGenerationRequestStatusUseCase {

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
    public GenerationRequest create(CreateGenerationRequestUseCase.Command command) {
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
                null,
                null,
                null,
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
    public boolean deleteById(UUID id) {
        return generationRequestRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GenerationRequest> getAll(GenerationRequestFilters filters) {
        return generationRequestRepository.findAllByFilters(filters);
    }

    @Override
    public Result updateStatus(UUID requestId, UpdateGenerationRequestStatusUseCase.Command command) {
        Optional<GenerationRequest> existingRequest = generationRequestRepository.findById(requestId);
        if (existingRequest.isEmpty()) {
            return Result.NOT_FOUND;
        }

        GenerationRequest current = existingRequest.get();
        if (!isValidTransition(current.status(), command.status())) {
            return Result.INVALID_TRANSITION;
        }

        GenerationRequest updated = new GenerationRequest(
                current.id(),
                current.name(),
                current.template(),
                current.database(),
                current.restApi(),
                current.security(),
                current.messaging(),
                current.deploymentTarget(),
                command.status(),
                current.specJson(),
                command.message() != null ? command.message() : current.message(),
                command.artifactRef() != null ? command.artifactRef() : current.artifactRef(),
                command.imageRef() != null ? command.imageRef() : current.imageRef(),
                current.createdAt(),
                OffsetDateTime.now()
        );
        generationRequestRepository.save(updated);
        return Result.UPDATED;
    }

    private boolean isValidTransition(GenerationRequestStatus current, GenerationRequestStatus target) {
        if (target == null || !isWorkerStatus(target)) {
            return false;
        }
        if (current == target) {
            return true;
        }
        return current == GenerationRequestStatus.RECEIVED && target == GenerationRequestStatus.GENERATING
                || current == GenerationRequestStatus.GENERATING
                && (target == GenerationRequestStatus.GENERATED || target == GenerationRequestStatus.FAILED);
    }

    private boolean isWorkerStatus(GenerationRequestStatus status) {
        return status == GenerationRequestStatus.GENERATING
                || status == GenerationRequestStatus.GENERATED
                || status == GenerationRequestStatus.FAILED;
    }
}
