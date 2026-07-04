package com.scaffoldops.generatorapi.application.service;

import com.scaffoldops.generatorapi.application.model.GenerationRequestFilters;
import com.scaffoldops.generatorapi.application.port.in.CreateGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.UpdateGenerationRequestStatusUseCase;
import com.scaffoldops.generatorapi.application.port.out.GenerationRequestEventPublisher;
import com.scaffoldops.generatorapi.application.port.out.GenerationRequestRepository;
import com.scaffoldops.generatorapi.domain.event.ArtifactCleanupRequestedEvent;
import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.domain.model.GenerationRequestStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationRequestServiceTest {

    @Mock
    private GenerationRequestRepository generationRequestRepository;

    @Mock
    private GenerationRequestEventPublisher generationRequestEventPublisher;

    @InjectMocks
    private GenerationRequestService generationRequestService;

    @Test
    void shouldCreateGenerationRequestWithReceivedStatus() {
        when(generationRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GenerationRequest created = generationRequestService.create(
                new CreateGenerationRequestUseCase.Command(
                        "billing-service",
                        "spring-boot-hexagonal",
                        true,
                        true,
                        true,
                        false,
                        DeploymentTarget.KUBERNETES,
                        "{\"name\":\"billing-service\"}"
                )
        );

        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationRequestRepository).save(captor.capture());
        GenerationRequest saved = captor.getValue();

        assertThat(created.id()).isNotNull();
        assertThat(saved.name()).isEqualTo("billing-service");
        assertThat(saved.template()).isEqualTo("spring-boot-hexagonal");
        assertThat(saved.status()).isEqualTo(GenerationRequestStatus.RECEIVED);
        assertThat(saved.specJson()).isEqualTo("{\"name\":\"billing-service\"}");
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.updatedAt()).isEqualTo(saved.createdAt());
        verify(generationRequestEventPublisher).publishGenerationRequested(any());
    }

    @Test
    void shouldDelegateFiltersWhenListingGenerationRequests() {
        GenerationRequestFilters filters = new GenerationRequestFilters(
                "billing-service",
                "spring-boot-hexagonal",
                GenerationRequestStatus.RECEIVED,
                DeploymentTarget.KUBERNETES,
                true,
                true,
                true,
                false
        );
        when(generationRequestRepository.findAllByFilters(filters)).thenReturn(List.of(sample()));

        List<GenerationRequest> results = generationRequestService.getAll(filters);

        assertThat(results).hasSize(1);
        verify(generationRequestRepository).findAllByFilters(filters);
    }

    @Test
    void shouldDeleteGenerationRequestById() {
        GenerationRequest request = sample();
        UUID id = request.id();
        when(generationRequestRepository.findById(id)).thenReturn(Optional.of(request));
        when(generationRequestRepository.deleteById(id)).thenReturn(true);

        boolean deleted = generationRequestService.deleteById(id);

        assertThat(deleted).isTrue();
        verify(generationRequestRepository).deleteById(id);
        ArgumentCaptor<ArtifactCleanupRequestedEvent> captor =
                ArgumentCaptor.forClass(ArtifactCleanupRequestedEvent.class);
        verify(generationRequestEventPublisher).publishArtifactCleanupRequested(captor.capture());
        assertThat(captor.getValue().requestId()).isEqualTo(id);
        assertThat(captor.getValue().name()).isEqualTo(request.name());
        assertThat(captor.getValue().deletedAt()).isNotNull();
    }

    @Test
    void shouldReturnFalseWhenDeletingMissingGenerationRequest() {
        UUID id = UUID.randomUUID();
        when(generationRequestRepository.findById(id)).thenReturn(Optional.empty());

        boolean deleted = generationRequestService.deleteById(id);

        assertThat(deleted).isFalse();
        verify(generationRequestRepository, never()).deleteById(id);
        verify(generationRequestEventPublisher, never()).publishArtifactCleanupRequested(any());
    }

    @Test
    void shouldNotPublishArtifactCleanupWhenDeleteFailsAfterLookup() {
        GenerationRequest request = sample();
        UUID id = request.id();
        when(generationRequestRepository.findById(id)).thenReturn(Optional.of(request));
        when(generationRequestRepository.deleteById(id)).thenReturn(false);

        boolean deleted = generationRequestService.deleteById(id);

        assertThat(deleted).isFalse();
        verify(generationRequestEventPublisher, never()).publishArtifactCleanupRequested(any());
    }

    @Test
    void shouldUpdateGenerationStatusAndMetadata() {
        GenerationRequest current = sample();
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        when(generationRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateGenerationRequestStatusUseCase.Result result = generationRequestService.updateStatus(
                current.id(),
                new UpdateGenerationRequestStatusUseCase.Command(
                        GenerationRequestStatus.GENERATING,
                        "Generation started",
                        "s3://artifacts/billing.zip",
                        "registry/billing:latest"
                )
        );

        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationRequestRepository).save(captor.capture());
        GenerationRequest saved = captor.getValue();

        assertThat(result).isEqualTo(UpdateGenerationRequestStatusUseCase.Result.UPDATED);
        assertThat(saved.status()).isEqualTo(GenerationRequestStatus.GENERATING);
        assertThat(saved.message()).isEqualTo("Generation started");
        assertThat(saved.artifactRef()).isEqualTo("s3://artifacts/billing.zip");
        assertThat(saved.imageRef()).isEqualTo("registry/billing:latest");
        assertThat(saved.updatedAt()).isAfter(saved.createdAt());
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingMissingGenerationRequest() {
        UUID id = UUID.randomUUID();
        when(generationRequestRepository.findById(id)).thenReturn(Optional.empty());

        UpdateGenerationRequestStatusUseCase.Result result = generationRequestService.updateStatus(
                id,
                new UpdateGenerationRequestStatusUseCase.Command(
                        GenerationRequestStatus.GENERATING,
                        null,
                        null,
                        null
                )
        );

        assertThat(result).isEqualTo(UpdateGenerationRequestStatusUseCase.Result.NOT_FOUND);
        verify(generationRequestRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidGenerationStatusTransition() {
        GenerationRequest current = sample();
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));

        UpdateGenerationRequestStatusUseCase.Result result = generationRequestService.updateStatus(
                current.id(),
                new UpdateGenerationRequestStatusUseCase.Command(
                        GenerationRequestStatus.GENERATED,
                        null,
                        null,
                        null
                )
        );

        assertThat(result).isEqualTo(UpdateGenerationRequestStatusUseCase.Result.INVALID_TRANSITION);
        verify(generationRequestRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = GenerationRequestStatus.class, names = {"GENERATED", "FAILED"})
    void shouldAllowTerminalGenerationStatusTransitions(GenerationRequestStatus targetStatus) {
        GenerationRequest current = withStatus(sample(), GenerationRequestStatus.GENERATING);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        when(generationRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateGenerationRequestStatusUseCase.Result result = generationRequestService.updateStatus(
                current.id(),
                new UpdateGenerationRequestStatusUseCase.Command(targetStatus, null, null, null)
        );

        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationRequestRepository).save(captor.capture());
        assertThat(result).isEqualTo(UpdateGenerationRequestStatusUseCase.Result.UPDATED);
        assertThat(captor.getValue().status()).isEqualTo(targetStatus);
    }

    @Test
    void shouldAllowIdempotentStatusUpdateAndRetainMissingMetadata() {
        GenerationRequest current = new GenerationRequest(
                UUID.randomUUID(),
                "billing-service",
                "spring-boot-hexagonal",
                true,
                true,
                true,
                false,
                DeploymentTarget.KUBERNETES,
                GenerationRequestStatus.GENERATING,
                "{\"name\":\"billing-service\"}",
                "Generation started",
                "s3://artifacts/billing.zip",
                "registry/billing:latest",
                OffsetDateTime.parse("2026-03-07T10:15:30Z"),
                OffsetDateTime.parse("2026-03-07T10:16:30Z")
        );
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        when(generationRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateGenerationRequestStatusUseCase.Result result = generationRequestService.updateStatus(
                current.id(),
                new UpdateGenerationRequestStatusUseCase.Command(
                        GenerationRequestStatus.GENERATING,
                        null,
                        null,
                        null
                )
        );

        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationRequestRepository).save(captor.capture());
        assertThat(result).isEqualTo(UpdateGenerationRequestStatusUseCase.Result.UPDATED);
        assertThat(captor.getValue().message()).isEqualTo(current.message());
        assertThat(captor.getValue().artifactRef()).isEqualTo(current.artifactRef());
        assertThat(captor.getValue().imageRef()).isEqualTo(current.imageRef());
    }

    private GenerationRequest sample() {
        return new GenerationRequest(
                UUID.randomUUID(),
                "billing-service",
                "spring-boot-hexagonal",
                true,
                true,
                true,
                false,
                DeploymentTarget.KUBERNETES,
                GenerationRequestStatus.RECEIVED,
                "{\"name\":\"billing-service\"}",
                null,
                null,
                null,
                OffsetDateTime.parse("2026-03-07T10:15:30Z"),
                OffsetDateTime.parse("2026-03-07T10:15:30Z")
        );
    }

    private GenerationRequest withStatus(GenerationRequest request, GenerationRequestStatus status) {
        return new GenerationRequest(
                request.id(),
                request.name(),
                request.template(),
                request.database(),
                request.restApi(),
                request.security(),
                request.messaging(),
                request.deploymentTarget(),
                status,
                request.specJson(),
                request.message(),
                request.artifactRef(),
                request.imageRef(),
                request.createdAt(),
                request.updatedAt()
        );
    }
}
