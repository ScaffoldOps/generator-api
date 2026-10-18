package com.scaffoldops.generatorapi.application.service;

import com.scaffoldops.generatorapi.application.model.GenerationRequestFilters;
import com.scaffoldops.generatorapi.application.port.in.CreateGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.RequestDeploymentUseCase;
import com.scaffoldops.generatorapi.application.port.in.RequestUndeploymentUseCase;
import com.scaffoldops.generatorapi.application.port.in.UpdateDeploymentRequestStatusUseCase;
import com.scaffoldops.generatorapi.application.port.in.UpdateGenerationRequestStatusUseCase;
import com.scaffoldops.generatorapi.application.port.out.GenerationRequestEventPublisher;
import com.scaffoldops.generatorapi.application.port.out.GenerationRequestRepository;
import com.scaffoldops.generatorapi.domain.event.ArtifactCleanupRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.DeploymentRequestedEvent;
import com.scaffoldops.generatorapi.domain.event.UndeploymentRequestedEvent;
import com.scaffoldops.generatorapi.domain.model.DeploymentStatus;
import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.domain.model.GenerationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void shouldCreateGenerationRequestWithInitialGenerationAndDeploymentStates() {
        when(generationRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GenerationRequest created = generationRequestService.create(new CreateGenerationRequestUseCase.Command(
                "billing-service",
                "spring-boot-hexagonal",
                true,
                true,
                true,
                false,
                DeploymentTarget.KUBERNETES,
                "{\"name\":\"billing-service\"}"
        ));

        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationRequestRepository).save(captor.capture());
        GenerationRequest saved = captor.getValue();

        assertThat(created.id()).isNotNull();
        assertThat(saved.generationStatus()).isEqualTo(GenerationStatus.RECEIVED);
        assertThat(saved.deploymentStatus()).isEqualTo(DeploymentStatus.NOT_DEPLOYED);
        assertThat(saved.specJson()).isEqualTo("{\"name\":\"billing-service\"}");
        assertThat(saved.updatedAt()).isEqualTo(saved.createdAt());
        ArgumentCaptor<com.scaffoldops.generatorapi.domain.event.GenerationRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(com.scaffoldops.generatorapi.domain.event.GenerationRequestedEvent.class);
        verify(generationRequestEventPublisher).publishGenerationRequested(eventCaptor.capture());
        assertThat(eventCaptor.getValue().generationStatus()).isEqualTo(GenerationStatus.RECEIVED);
        assertThat(eventCaptor.getValue().requestId()).isEqualTo(saved.id());
        verify(generationRequestEventPublisher, never()).publishDeploymentRequested(any());
    }

    @Test
    void shouldDelegateFiltersWhenListingGenerationRequests() {
        GenerationRequestFilters filters = new GenerationRequestFilters(
                "billing-service",
                "spring-boot-hexagonal",
                GenerationStatus.RECEIVED,
                DeploymentStatus.NOT_DEPLOYED,
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
    void shouldRequestDeploymentAfterGenerationHasCompleted() {
        GenerationRequest current = withStatuses(sample(), GenerationStatus.GENERATED, DeploymentStatus.NOT_DEPLOYED);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        when(generationRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RequestDeploymentUseCase.Result result = generationRequestService.requestDeployment(deployCommand(current.id()));

        ArgumentCaptor<GenerationRequest> requestCaptor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationRequestRepository).save(requestCaptor.capture());
        assertThat(result).isEqualTo(RequestDeploymentUseCase.Result.ACCEPTED);
        assertThat(requestCaptor.getValue().generationStatus()).isEqualTo(GenerationStatus.GENERATED);
        assertThat(requestCaptor.getValue().deploymentStatus()).isEqualTo(DeploymentStatus.DEPLOYING);
        assertThat(requestCaptor.getValue().deploymentNamespace()).isEqualTo("scaffoldops-dev");

        ArgumentCaptor<DeploymentRequestedEvent> eventCaptor = ArgumentCaptor.forClass(DeploymentRequestedEvent.class);
        verify(generationRequestEventPublisher).publishDeploymentRequested(eventCaptor.capture());
        assertThat(eventCaptor.getValue().requestId()).isEqualTo(current.id());
        assertThat(eventCaptor.getValue().serviceName()).isEqualTo(current.name());
        assertThat(eventCaptor.getValue().deploymentTarget()).isEqualTo(DeploymentTarget.KUBERNETES);
        assertThat(eventCaptor.getValue().artifactRef()).isEqualTo(current.artifactRef());
        assertThat(eventCaptor.getValue().imageRef()).isEqualTo(current.imageRef());
        assertThat(eventCaptor.getValue().namespace()).isEqualTo("scaffoldops-dev");
        assertThat(eventCaptor.getValue().replicas()).isEqualTo(2);
        assertThat(eventCaptor.getValue().requestedAt()).isNotNull();
    }

    @Test
    void shouldRejectDeploymentWhenArtifactRefIsMissing() {
        GenerationRequest current = withArtifactAndImageRefs(
                withStatuses(sample(), GenerationStatus.GENERATED, DeploymentStatus.NOT_DEPLOYED),
                null,
                "registry/billing:latest"
        );
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));

        RequestDeploymentUseCase.Result result = generationRequestService.requestDeployment(deployCommand(current.id()));

        assertThat(result).isEqualTo(RequestDeploymentUseCase.Result.INVALID_TRANSITION);
        verify(generationRequestRepository, never()).save(any());
        verify(generationRequestEventPublisher, never()).publishDeploymentRequested(any());
    }

    @Test
    void shouldRejectDeploymentWhenImageRefIsMissing() {
        GenerationRequest current = withArtifactAndImageRefs(
                withStatuses(sample(), GenerationStatus.GENERATED, DeploymentStatus.NOT_DEPLOYED),
                "s3://artifacts/billing.zip",
                " "
        );
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));

        RequestDeploymentUseCase.Result result = generationRequestService.requestDeployment(deployCommand(current.id()));

        assertThat(result).isEqualTo(RequestDeploymentUseCase.Result.INVALID_TRANSITION);
        verify(generationRequestRepository, never()).save(any());
        verify(generationRequestEventPublisher, never()).publishDeploymentRequested(any());
    }

    @Test
    void shouldRejectDeploymentWhenNamespaceIsBlank() {
        GenerationRequest current = withStatuses(sample(), GenerationStatus.GENERATED, DeploymentStatus.NOT_DEPLOYED);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));

        RequestDeploymentUseCase.Result result = generationRequestService.requestDeployment(
                new RequestDeploymentUseCase.Command(current.id(), " ", 1)
        );

        assertThat(result).isEqualTo(RequestDeploymentUseCase.Result.INVALID_TRANSITION);
        verify(generationRequestRepository, never()).save(any());
        verify(generationRequestEventPublisher, never()).publishDeploymentRequested(any());
    }

    @Test
    void shouldRejectDeploymentBeforeGenerationHasCompleted() {
        GenerationRequest current = withStatuses(sample(), GenerationStatus.GENERATING, DeploymentStatus.NOT_DEPLOYED);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));

        RequestDeploymentUseCase.Result result = generationRequestService.requestDeployment(deployCommand(current.id()));

        assertThat(result).isEqualTo(RequestDeploymentUseCase.Result.INVALID_TRANSITION);
        verify(generationRequestRepository, never()).save(any());
        verify(generationRequestEventPublisher, never()).publishDeploymentRequested(any());
    }

    @ParameterizedTest
    @EnumSource(value = DeploymentStatus.class, names = {
            "DEPLOYMENT_REQUESTED",
            "DEPLOYING",
            "DEPLOYED",
            "UNDEPLOYING"
    })
    void shouldRejectDuplicateDeployRequests(DeploymentStatus deploymentStatus) {
        GenerationRequest current = withStatuses(sample(), GenerationStatus.GENERATED, deploymentStatus);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));

        RequestDeploymentUseCase.Result result = generationRequestService.requestDeployment(deployCommand(current.id()));

        assertThat(result).isEqualTo(RequestDeploymentUseCase.Result.INVALID_TRANSITION);
        verify(generationRequestRepository, never()).save(any());
        verify(generationRequestEventPublisher, never()).publishDeploymentRequested(any());
    }

    @Test
    void shouldRequestUndeploymentFromDeployed() {
        GenerationRequest current = withNamespace(withStatuses(sample(), GenerationStatus.GENERATED, DeploymentStatus.DEPLOYED));
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        when(generationRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RequestUndeploymentUseCase.Result result = generationRequestService.requestUndeployment(current.id());

        ArgumentCaptor<GenerationRequest> requestCaptor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationRequestRepository).save(requestCaptor.capture());
        assertThat(result).isEqualTo(RequestUndeploymentUseCase.Result.ACCEPTED);
        assertThat(requestCaptor.getValue().deploymentStatus()).isEqualTo(DeploymentStatus.UNDEPLOYING);

        ArgumentCaptor<UndeploymentRequestedEvent> eventCaptor = ArgumentCaptor.forClass(UndeploymentRequestedEvent.class);
        verify(generationRequestEventPublisher).publishUndeploymentRequested(eventCaptor.capture());
        assertThat(eventCaptor.getValue().requestId()).isEqualTo(current.id());
        assertThat(eventCaptor.getValue().serviceName()).isEqualTo(current.name());
        assertThat(eventCaptor.getValue().artifactRef()).isEqualTo(current.artifactRef());
        assertThat(eventCaptor.getValue().imageRef()).isEqualTo(current.imageRef());
        assertThat(eventCaptor.getValue().namespace()).isEqualTo(current.deploymentNamespace());
    }

    @ParameterizedTest
    @EnumSource(value = DeploymentStatus.class, names = {
            "NOT_DEPLOYED",
            "DEPLOYMENT_REQUESTED",
            "DEPLOYING",
            "UNDEPLOYMENT_REQUESTED",
            "UNDEPLOYING"
    })
    void shouldRejectDuplicateOrInvalidUndeployRequests(DeploymentStatus deploymentStatus) {
        GenerationRequest current = withStatuses(sample(), GenerationStatus.GENERATED, deploymentStatus);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));

        RequestUndeploymentUseCase.Result result = generationRequestService.requestUndeployment(current.id());

        assertThat(result).isEqualTo(RequestUndeploymentUseCase.Result.INVALID_TRANSITION);
        verify(generationRequestRepository, never()).save(any());
        verify(generationRequestEventPublisher, never()).publishUndeploymentRequested(any());
    }

    @Test
    void shouldReturnNotFoundWhenRequestingDeploymentForMissingGenerationRequest() {
        UUID id = UUID.randomUUID();
        when(generationRequestRepository.findById(id)).thenReturn(Optional.empty());

        RequestDeploymentUseCase.Result result = generationRequestService.requestDeployment(deployCommand(id));

        assertThat(result).isEqualTo(RequestDeploymentUseCase.Result.NOT_FOUND);
        verify(generationRequestRepository, never()).save(any());
    }

    @Test
    void shouldReturnNotFoundWhenRequestingUndeploymentForMissingGenerationRequest() {
        UUID id = UUID.randomUUID();
        when(generationRequestRepository.findById(id)).thenReturn(Optional.empty());

        RequestUndeploymentUseCase.Result result = generationRequestService.requestUndeployment(id);

        assertThat(result).isEqualTo(RequestUndeploymentUseCase.Result.NOT_FOUND);
        verify(generationRequestRepository, never()).save(any());
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
        assertThat(captor.getValue().artifactRef()).isEqualTo(request.artifactRef());
        assertThat(captor.getValue().imageRef()).isEqualTo(request.imageRef());
        assertThat(captor.getValue().deploymentNamespace()).isEqualTo(request.deploymentNamespace());
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
    void shouldUpdateGenerationStatusAndMetadataOnly() {
        GenerationRequest current = sample();
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        when(generationRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateGenerationRequestStatusUseCase.Result result = generationRequestService.updateGenerationStatus(
                current.id(),
                new UpdateGenerationRequestStatusUseCase.Command(
                        GenerationStatus.GENERATING,
                        "Generation started",
                        "s3://artifacts/billing.zip",
                        "registry/billing:latest"
                )
        );

        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationRequestRepository).save(captor.capture());
        GenerationRequest saved = captor.getValue();

        assertThat(result).isEqualTo(UpdateGenerationRequestStatusUseCase.Result.UPDATED);
        assertThat(saved.generationStatus()).isEqualTo(GenerationStatus.GENERATING);
        assertThat(saved.deploymentStatus()).isEqualTo(current.deploymentStatus());
        assertThat(saved.message()).isEqualTo("Generation started");
        assertThat(saved.artifactRef()).isEqualTo("s3://artifacts/billing.zip");
        assertThat(saved.imageRef()).isEqualTo("registry/billing:latest");
    }

    @Test
    void shouldUpdateDeploymentStatusAndMetadataOnly() {
        GenerationRequest current = withStatuses(sample(), GenerationStatus.GENERATED, DeploymentStatus.DEPLOYMENT_REQUESTED);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        when(generationRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateDeploymentRequestStatusUseCase.Result result = generationRequestService.updateDeploymentStatus(
                current.id(),
                new UpdateDeploymentRequestStatusUseCase.Command(
                        DeploymentStatus.DEPLOYING,
                        "Deployment started",
                        "registry/billing:latest"
                )
        );

        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationRequestRepository).save(captor.capture());
        GenerationRequest saved = captor.getValue();

        assertThat(result).isEqualTo(UpdateDeploymentRequestStatusUseCase.Result.UPDATED);
        assertThat(saved.generationStatus()).isEqualTo(current.generationStatus());
        assertThat(saved.deploymentStatus()).isEqualTo(DeploymentStatus.DEPLOYING);
        assertThat(saved.message()).isEqualTo("Deployment started");
        assertThat(saved.artifactRef()).isEqualTo(current.artifactRef());
        assertThat(saved.imageRef()).isEqualTo("registry/billing:latest");
    }

    @Test
    void shouldRejectInvalidGenerationStatusTransition() {
        GenerationRequest current = sample();
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));

        UpdateGenerationRequestStatusUseCase.Result result = generationRequestService.updateGenerationStatus(
                current.id(),
                new UpdateGenerationRequestStatusUseCase.Command(GenerationStatus.GENERATED, null, null, null)
        );

        assertThat(result).isEqualTo(UpdateGenerationRequestStatusUseCase.Result.INVALID_TRANSITION);
        verify(generationRequestRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidDeploymentStatusTransition() {
        GenerationRequest current = withStatuses(sample(), GenerationStatus.GENERATED, DeploymentStatus.NOT_DEPLOYED);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));

        UpdateDeploymentRequestStatusUseCase.Result result = generationRequestService.updateDeploymentStatus(
                current.id(),
                new UpdateDeploymentRequestStatusUseCase.Command(DeploymentStatus.DEPLOYED, null, null)
        );

        assertThat(result).isEqualTo(UpdateDeploymentRequestStatusUseCase.Result.INVALID_TRANSITION);
        verify(generationRequestRepository, never()).save(any());
    }

    @Test
    void shouldAllowIdempotentGenerationStatusUpdateAndRetainMissingMetadata() {
        GenerationRequest current = withStatuses(sample(), GenerationStatus.GENERATING, DeploymentStatus.NOT_DEPLOYED);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        when(generationRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateGenerationRequestStatusUseCase.Result result = generationRequestService.updateGenerationStatus(
                current.id(),
                new UpdateGenerationRequestStatusUseCase.Command(GenerationStatus.GENERATING, null, null, null)
        );

        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationRequestRepository).save(captor.capture());
        assertThat(result).isEqualTo(UpdateGenerationRequestStatusUseCase.Result.UPDATED);
        assertThat(captor.getValue().message()).isEqualTo(current.message());
        assertThat(captor.getValue().artifactRef()).isEqualTo(current.artifactRef());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {" ", "\t"})
    void shouldRejectGeneratedAndDeploymentWithMissingReferences(String missing) {
        for (boolean missingArtifact : new boolean[] {true, false}) {
            GenerationRequest current = withArtifactAndImageRefs(
                    withStatuses(sample(), GenerationStatus.GENERATING, DeploymentStatus.NOT_DEPLOYED),
                    missingArtifact ? missing : "s3://artifacts/billing.zip",
                    missingArtifact ? "registry/billing:latest" : missing);
            when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
            assertThat(generationRequestService.updateGenerationStatus(current.id(),
                    new UpdateGenerationRequestStatusUseCase.Command(GenerationStatus.GENERATED, null, null, null)))
                    .isEqualTo(UpdateGenerationRequestStatusUseCase.Result.INVALID_REFERENCES);
            GenerationRequest generated = withStatuses(current, GenerationStatus.GENERATED, DeploymentStatus.NOT_DEPLOYED);
            when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(generated));
            assertThat(generationRequestService.requestDeployment(deployCommand(current.id())))
                    .isEqualTo(RequestDeploymentUseCase.Result.INVALID_TRANSITION);
        }
        verify(generationRequestRepository, never()).save(any());
        verify(generationRequestEventPublisher, never()).publishDeploymentRequested(any());
    }

    @Test
    void shouldAcceptGeneratedOnlyWithBothReferences() {
        GenerationRequest current = withStatuses(sample(), GenerationStatus.GENERATING, DeploymentStatus.NOT_DEPLOYED);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        assertThat(generationRequestService.updateGenerationStatus(current.id(),
                new UpdateGenerationRequestStatusUseCase.Command(GenerationStatus.GENERATED,
                        "Generation completed successfully", "s3://artifacts/billing.zip", "registry/billing:latest", null, 1)))
                .isEqualTo(UpdateGenerationRequestStatusUseCase.Result.UPDATED);
        ArgumentCaptor<GenerationRequest> saved = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationRequestRepository).save(saved.capture());
        assertThat(saved.getValue().generationStatus()).isEqualTo(GenerationStatus.GENERATED);
        assertThat(saved.getValue().retryCount()).isEqualTo(1);
        assertThat(saved.getValue().failureStage()).isNull();
    }

    @Test
    void shouldPersistImageFailureDiagnosticsAndClearStaleImage() {
        GenerationRequest current = withStatuses(sample(), GenerationStatus.GENERATING, DeploymentStatus.NOT_DEPLOYED);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        assertThat(generationRequestService.updateGenerationStatus(current.id(),
                new UpdateGenerationRequestStatusUseCase.Command(GenerationStatus.GENERATION_FAILED,
                        "Docker push failed after 3 attempts", null, null, "IMAGE_PUSH", 2)))
                .isEqualTo(UpdateGenerationRequestStatusUseCase.Result.UPDATED);
        ArgumentCaptor<GenerationRequest> saved = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationRequestRepository).save(saved.capture());
        assertThat(saved.getValue().artifactRef()).isEqualTo(current.artifactRef());
        assertThat(saved.getValue().imageRef()).isNull();
        assertThat(saved.getValue().failureStage()).isEqualTo("IMAGE_PUSH");
        assertThat(saved.getValue().retryCount()).isEqualTo(2);
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
                GenerationStatus.RECEIVED,
                DeploymentStatus.NOT_DEPLOYED,
                "{\"name\":\"billing-service\"}",
                "Initial message",
                "s3://artifacts/billing.zip",
                "registry/billing:previous",
                null,
                OffsetDateTime.parse("2026-03-07T10:15:30Z"),
                OffsetDateTime.parse("2026-03-07T10:15:30Z")
        );
    }

    private GenerationRequest withStatuses(
            GenerationRequest request,
            GenerationStatus generationStatus,
            DeploymentStatus deploymentStatus
    ) {
        return new GenerationRequest(
                request.id(),
                request.name(),
                request.template(),
                request.database(),
                request.restApi(),
                request.security(),
                request.messaging(),
                request.deploymentTarget(),
                generationStatus,
                deploymentStatus,
                request.specJson(),
                request.message(),
                request.artifactRef(),
                request.imageRef(),
                request.deploymentNamespace(),
                request.createdAt(),
                request.updatedAt()
        );
    }

    private GenerationRequest withArtifactAndImageRefs(
            GenerationRequest request,
            String artifactRef,
            String imageRef
    ) {
        return new GenerationRequest(
                request.id(),
                request.name(),
                request.template(),
                request.database(),
                request.restApi(),
                request.security(),
                request.messaging(),
                request.deploymentTarget(),
                request.generationStatus(),
                request.deploymentStatus(),
                request.specJson(),
                request.message(),
                artifactRef,
                imageRef,
                request.deploymentNamespace(),
                request.createdAt(),
                request.updatedAt()
        );
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Upper", "bad_name", "-bad", "bad-", "a.b"})
    void rejectsInvalidKubernetesNamespace(String namespace) {
        GenerationRequest current = withStatuses(sample(), GenerationStatus.GENERATED, DeploymentStatus.NOT_DEPLOYED);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        assertThat(generationRequestService.requestDeployment(new RequestDeploymentUseCase.Command(current.id(), namespace, 1)))
                .isEqualTo(RequestDeploymentUseCase.Result.INVALID_TRANSITION);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, -1, 21})
    void rejectsOutOfRangeReplicas(int replicas) {
        GenerationRequest current = withStatuses(sample(), GenerationStatus.GENERATED, DeploymentStatus.NOT_DEPLOYED);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        assertThat(generationRequestService.requestDeployment(new RequestDeploymentUseCase.Command(current.id(), "generated-dev", replicas)))
                .isEqualTo(RequestDeploymentUseCase.Result.INVALID_TRANSITION);
    }

    @Test
    void completesUndeployAndRetainsAssets() {
        GenerationRequest current = withStatuses(sample(), GenerationStatus.GENERATED, DeploymentStatus.UNDEPLOYING);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        assertThat(generationRequestService.updateDeploymentStatus(current.id(),
                new UpdateDeploymentRequestStatusUseCase.Command(DeploymentStatus.NOT_DEPLOYED, "Removed", null, current.deploymentNamespace())))
                .isEqualTo(UpdateDeploymentRequestStatusUseCase.Result.UPDATED);
        ArgumentCaptor<GenerationRequest> saved = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationRequestRepository).save(saved.capture());
        assertThat(saved.getValue().artifactRef()).isEqualTo(current.artifactRef());
        assertThat(saved.getValue().imageRef()).isEqualTo(current.imageRef());
        assertThat(saved.getValue().deploymentNamespace()).isEqualTo(current.deploymentNamespace());
    }

    private GenerationRequest withNamespace(GenerationRequest c) {
        return new GenerationRequest(c.id(), c.name(), c.template(), c.database(), c.restApi(), c.security(),
                c.messaging(), c.deploymentTarget(), c.generationStatus(), c.deploymentStatus(), c.specJson(),
                c.message(), c.artifactRef(), c.imageRef(), "generated-dev", c.createdAt(), c.updatedAt(),
                c.failureStage(), c.retryCount());
    }

    @Test
    void allowsUndeployFromFailureWithoutImage() {
        GenerationRequest current = withNamespace(withArtifactAndImageRefs(
                withStatuses(sample(), GenerationStatus.GENERATED, DeploymentStatus.DEPLOYMENT_FAILED), "s3://a/b", null));
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        when(generationRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(generationRequestService.requestUndeployment(current.id())).isEqualTo(RequestUndeploymentUseCase.Result.ACCEPTED);
    }

    @Test
    void rejectsUndeployWithoutNamespace() {
        GenerationRequest current = withStatuses(sample(), GenerationStatus.GENERATED, DeploymentStatus.DEPLOYED);
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        assertThat(generationRequestService.requestUndeployment(current.id())).isEqualTo(RequestUndeploymentUseCase.Result.INVALID_TRANSITION);
    }

    @Test
    void rejectsCallbackForAnotherNamespace() {
        GenerationRequest current = withNamespace(withStatuses(sample(), GenerationStatus.GENERATED, DeploymentStatus.DEPLOYING));
        when(generationRequestRepository.findById(current.id())).thenReturn(Optional.of(current));
        assertThat(generationRequestService.updateDeploymentStatus(current.id(),
                new UpdateDeploymentRequestStatusUseCase.Command(DeploymentStatus.DEPLOYED, null, null, "another")))
                .isEqualTo(UpdateDeploymentRequestStatusUseCase.Result.INVALID_TRANSITION);
    }

    private RequestDeploymentUseCase.Command deployCommand(UUID id) {
        return new RequestDeploymentUseCase.Command(id, "scaffoldops-dev", 2);
    }
}
