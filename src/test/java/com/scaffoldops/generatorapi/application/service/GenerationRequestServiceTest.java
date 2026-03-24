package com.scaffoldops.generatorapi.application.service;

import com.scaffoldops.generatorapi.application.model.GenerationRequestFilters;
import com.scaffoldops.generatorapi.application.port.in.CreateGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.out.GenerationRequestEventPublisher;
import com.scaffoldops.generatorapi.application.port.out.GenerationRequestRepository;
import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.domain.model.GenerationRequestStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
                OffsetDateTime.parse("2026-03-07T10:15:30Z"),
                OffsetDateTime.parse("2026-03-07T10:15:30Z")
        );
    }
}
