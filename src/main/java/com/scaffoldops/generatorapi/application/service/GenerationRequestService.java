package com.scaffoldops.generatorapi.application.service;

import com.scaffoldops.generatorapi.application.model.GenerationRequestFilters;
import com.scaffoldops.generatorapi.application.port.in.CreateGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.DeleteGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.GetGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.ListGenerationRequestsUseCase;
import com.scaffoldops.generatorapi.application.port.in.RequestDeploymentUseCase;
import com.scaffoldops.generatorapi.application.port.in.RequestUndeploymentUseCase;
import com.scaffoldops.generatorapi.application.port.in.UpdateDeploymentRequestStatusUseCase;
import com.scaffoldops.generatorapi.application.port.in.UpdateGenerationRequestStatusUseCase;
import com.scaffoldops.generatorapi.application.port.out.GenerationRequestEventPublisher;
import com.scaffoldops.generatorapi.application.port.out.GenerationRequestRepository;
import com.scaffoldops.generatorapi.domain.event.ArtifactCleanupRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.DeploymentRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.GenerationRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.UndeploymentRequestedEvent;
import com.scaffoldops.generatorapi.domain.model.DeploymentStatus;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.domain.model.GenerationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class GenerationRequestService implements CreateGenerationRequestUseCase, DeleteGenerationRequestUseCase,
        GetGenerationRequestUseCase, ListGenerationRequestsUseCase, RequestDeploymentUseCase,
        RequestUndeploymentUseCase, UpdateGenerationRequestStatusUseCase, UpdateDeploymentRequestStatusUseCase {

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
                GenerationStatus.RECEIVED,
                DeploymentStatus.NOT_DEPLOYED,
                command.specJson(),
                null,
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
                savedRequest.generationStatus(),
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
        Optional<GenerationRequest> existingRequest = generationRequestRepository.findById(id);
        if (existingRequest.isEmpty()) {
            return false;
        }

        boolean deleted = generationRequestRepository.deleteById(id);
        if (deleted) {
            GenerationRequest request = existingRequest.get();
            generationRequestEventPublisher.publishArtifactCleanupRequested(new ArtifactCleanupRequestedEvent(
                    request.id(),
                    request.name(),
                    OffsetDateTime.now()
            ));
        }
        return deleted;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GenerationRequest> getAll(GenerationRequestFilters filters) {
        return generationRequestRepository.findAllByFilters(filters);
    }

    @Override
    public RequestDeploymentUseCase.Result requestDeployment(RequestDeploymentUseCase.Command command) {
        Optional<GenerationRequest> existingRequest = generationRequestRepository.findById(command.requestId());
        if (existingRequest.isEmpty()) {
            return RequestDeploymentUseCase.Result.NOT_FOUND;
        }

        GenerationRequest current = existingRequest.get();
        if (!canRequestDeployment(current, command.namespace(), command.replicas())) {
            return RequestDeploymentUseCase.Result.INVALID_TRANSITION;
        }

        OffsetDateTime now = OffsetDateTime.now();
        GenerationRequest updated = new GenerationRequest(
                current.id(),
                current.name(),
                current.template(),
                current.database(),
                current.restApi(),
                current.security(),
                current.messaging(),
                current.deploymentTarget(),
                current.generationStatus(),
                DeploymentStatus.DEPLOYMENT_REQUESTED,
                current.specJson(),
                current.message(),
                current.artifactRef(),
                current.imageRef(),
                command.namespace(),
                current.createdAt(),
                now
        );
        GenerationRequest saved = generationRequestRepository.save(updated);
        generationRequestEventPublisher.publishDeploymentRequested(new DeploymentRequestedEvent(
                saved.id(),
                saved.name(),
                saved.deploymentTarget(),
                saved.artifactRef(),
                saved.imageRef(),
                saved.deploymentNamespace(),
                command.replicas(),
                now
        ));
        return RequestDeploymentUseCase.Result.ACCEPTED;
    }

    @Override
    public RequestUndeploymentUseCase.Result requestUndeployment(UUID requestId) {
        Optional<GenerationRequest> existingRequest = generationRequestRepository.findById(requestId);
        if (existingRequest.isEmpty()) {
            return RequestUndeploymentUseCase.Result.NOT_FOUND;
        }

        GenerationRequest current = existingRequest.get();
        if (current.deploymentStatus() != DeploymentStatus.DEPLOYED) {
            return RequestUndeploymentUseCase.Result.INVALID_TRANSITION;
        }

        OffsetDateTime now = OffsetDateTime.now();
        GenerationRequest updated = new GenerationRequest(
                current.id(),
                current.name(),
                current.template(),
                current.database(),
                current.restApi(),
                current.security(),
                current.messaging(),
                current.deploymentTarget(),
                current.generationStatus(),
                DeploymentStatus.UNDEPLOYMENT_REQUESTED,
                current.specJson(),
                current.message(),
                current.artifactRef(),
                current.imageRef(),
                current.deploymentNamespace(),
                current.createdAt(),
                now
        );
        GenerationRequest saved = generationRequestRepository.save(updated);
        generationRequestEventPublisher.publishUndeploymentRequested(new UndeploymentRequestedEvent(
                saved.id(),
                saved.name(),
                saved.deploymentTarget(),
                saved.artifactRef(),
                saved.imageRef(),
                saved.deploymentNamespace(),
                now
        ));
        return RequestUndeploymentUseCase.Result.ACCEPTED;
    }

    @Override
    public UpdateGenerationRequestStatusUseCase.Result updateGenerationStatus(
            UUID requestId,
            UpdateGenerationRequestStatusUseCase.Command command
    ) {
        Optional<GenerationRequest> existingRequest = generationRequestRepository.findById(requestId);
        if (existingRequest.isEmpty()) {
            return UpdateGenerationRequestStatusUseCase.Result.NOT_FOUND;
        }

        GenerationRequest current = existingRequest.get();
        if (!isValidGenerationTransition(current.generationStatus(), command.generationStatus())) {
            return UpdateGenerationRequestStatusUseCase.Result.INVALID_TRANSITION;
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
                command.generationStatus(),
                current.deploymentStatus(),
                current.specJson(),
                command.message() != null ? command.message() : current.message(),
                command.artifactRef() != null ? command.artifactRef() : current.artifactRef(),
                command.imageRef() != null ? command.imageRef() : current.imageRef(),
                current.deploymentNamespace(),
                current.createdAt(),
                OffsetDateTime.now()
        );
        generationRequestRepository.save(updated);
        return UpdateGenerationRequestStatusUseCase.Result.UPDATED;
    }

    @Override
    public UpdateDeploymentRequestStatusUseCase.Result updateDeploymentStatus(
            UUID requestId,
            UpdateDeploymentRequestStatusUseCase.Command command
    ) {
        Optional<GenerationRequest> existingRequest = generationRequestRepository.findById(requestId);
        if (existingRequest.isEmpty()) {
            return UpdateDeploymentRequestStatusUseCase.Result.NOT_FOUND;
        }

        GenerationRequest current = existingRequest.get();
        if (!isValidDeploymentTransition(current.deploymentStatus(), command.deploymentStatus())) {
            return UpdateDeploymentRequestStatusUseCase.Result.INVALID_TRANSITION;
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
                current.generationStatus(),
                command.deploymentStatus(),
                current.specJson(),
                command.message() != null ? command.message() : current.message(),
                current.artifactRef(),
                command.imageRef() != null ? command.imageRef() : current.imageRef(),
                current.deploymentNamespace(),
                current.createdAt(),
                OffsetDateTime.now()
        );
        generationRequestRepository.save(updated);
        return UpdateDeploymentRequestStatusUseCase.Result.UPDATED;
    }

    private boolean canRequestDeployment(GenerationRequest current, String namespace, Integer replicas) {
        if (current.generationStatus() != GenerationStatus.GENERATED) {
            return false;
        }
        if (isBlank(current.artifactRef()) || isBlank(current.imageRef()) || isBlank(namespace)
                || isInvalidReplicas(replicas)) {
            return false;
        }
        return switch (current.deploymentStatus()) {
            case NOT_DEPLOYED, DEPLOYMENT_FAILED -> true;
            case DEPLOYMENT_REQUESTED, DEPLOYING, DEPLOYED, UNDEPLOYMENT_REQUESTED, UNDEPLOYING -> false;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isInvalidReplicas(Integer replicas) {
        return replicas != null && replicas < 1;
    }

    private boolean isValidGenerationTransition(GenerationStatus current, GenerationStatus target) {
        if (target == null || target == GenerationStatus.RECEIVED) {
            return false;
        }
        if (current == target) {
            return true;
        }
        return current == GenerationStatus.RECEIVED && target == GenerationStatus.GENERATING
                || current == GenerationStatus.GENERATING
                && (target == GenerationStatus.GENERATED || target == GenerationStatus.GENERATION_FAILED);
    }

    private boolean isValidDeploymentTransition(DeploymentStatus current, DeploymentStatus target) {
        if (target == null || target == DeploymentStatus.NOT_DEPLOYED
                || target == DeploymentStatus.DEPLOYMENT_REQUESTED
                || target == DeploymentStatus.UNDEPLOYMENT_REQUESTED) {
            return false;
        }
        if (current == target) {
            return true;
        }
        return current == DeploymentStatus.DEPLOYMENT_REQUESTED && target == DeploymentStatus.DEPLOYING
                || current == DeploymentStatus.DEPLOYING
                && (target == DeploymentStatus.DEPLOYED || target == DeploymentStatus.DEPLOYMENT_FAILED)
                || current == DeploymentStatus.UNDEPLOYMENT_REQUESTED && target == DeploymentStatus.UNDEPLOYING
                || current == DeploymentStatus.UNDEPLOYING
                && (target == DeploymentStatus.NOT_DEPLOYED || target == DeploymentStatus.DEPLOYMENT_FAILED);
    }
}
