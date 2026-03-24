package com.scaffoldops.generatorapi.infrastructure.persistence.adapter;

import com.scaffoldops.generatorapi.application.model.GenerationRequestFilters;
import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.domain.model.GenerationRequestStatus;
import com.scaffoldops.generatorapi.infrastructure.persistence.mapper.GenerationRequestPersistenceMapper;
import com.scaffoldops.generatorapi.infrastructure.persistence.repository.SpringDataGenerationRequestJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaGenerationRequestRepositoryAdapter.class, GenerationRequestPersistenceMapper.class})
class JpaGenerationRequestRepositoryAdapterTest {

    @Autowired
    private JpaGenerationRequestRepositoryAdapter repositoryAdapter;

    @Autowired
    private SpringDataGenerationRequestJpaRepository jpaRepository;

    @Autowired
    private GenerationRequestPersistenceMapper persistenceMapper;

    @Test
    void shouldFilterWithAndSemanticsAndSortByCreatedAtDescending() {
        GenerationRequest newestMatch = persist(
                "payments-service",
                "spring-boot-hexagonal",
                true,
                true,
                true,
                false,
                DeploymentTarget.KUBERNETES,
                GenerationRequestStatus.RECEIVED,
                OffsetDateTime.parse("2026-03-08T10:15:30Z")
        );
        GenerationRequest olderMatch = persist(
                "billing-service",
                "spring-boot-hexagonal",
                true,
                true,
                true,
                false,
                DeploymentTarget.KUBERNETES,
                GenerationRequestStatus.RECEIVED,
                OffsetDateTime.parse("2026-03-07T10:15:30Z")
        );
        persist(
                "analytics-service",
                "spring-boot-hexagonal",
                false,
                true,
                true,
                false,
                DeploymentTarget.KUBERNETES,
                GenerationRequestStatus.RECEIVED,
                OffsetDateTime.parse("2026-03-09T10:15:30Z")
        );
        persist(
                "invoice-service",
                "spring-boot-hexagonal",
                true,
                true,
                true,
                false,
                DeploymentTarget.KUBERNETES,
                GenerationRequestStatus.GENERATING,
                OffsetDateTime.parse("2026-03-10T10:15:30Z")
        );

        List<GenerationRequest> results = repositoryAdapter.findAllByFilters(new GenerationRequestFilters(
                null,
                "spring-boot-hexagonal",
                GenerationRequestStatus.RECEIVED,
                DeploymentTarget.KUBERNETES,
                true,
                true,
                true,
                false
        ));

        assertThat(results)
                .extracting(GenerationRequest::id)
                .containsExactly(newestMatch.id(), olderMatch.id());
    }

    @Test
    void shouldReturnAllRequestsSortedByCreatedAtDescendingWhenFiltersAreEmpty() {
        GenerationRequest oldest = persist(
                "orders-service",
                "spring-boot-hexagonal",
                true,
                true,
                false,
                false,
                DeploymentTarget.KUBERNETES,
                GenerationRequestStatus.RECEIVED,
                OffsetDateTime.parse("2026-03-06T10:15:30Z")
        );
        GenerationRequest newest = persist(
                "payments-service",
                "spring-boot-hexagonal",
                true,
                true,
                true,
                false,
                DeploymentTarget.KUBERNETES,
                GenerationRequestStatus.GENERATING,
                OffsetDateTime.parse("2026-03-08T10:15:30Z")
        );

        List<GenerationRequest> results = repositoryAdapter.findAllByFilters(GenerationRequestFilters.empty());

        assertThat(results)
                .extracting(GenerationRequest::id)
                .containsSequence(newest.id(), oldest.id());
    }

    private GenerationRequest persist(
            String name,
            String template,
            boolean database,
            boolean restApi,
            boolean security,
            boolean messaging,
            DeploymentTarget deploymentTarget,
            GenerationRequestStatus status,
            OffsetDateTime createdAt
    ) {
        GenerationRequest generationRequest = new GenerationRequest(
                UUID.randomUUID(),
                name,
                template,
                database,
                restApi,
                security,
                messaging,
                deploymentTarget,
                status,
                "{\"name\":\"" + name + "\"}",
                createdAt,
                createdAt
        );
        return persistenceMapper.toDomain(jpaRepository.save(persistenceMapper.toEntity(generationRequest)));
    }
}
