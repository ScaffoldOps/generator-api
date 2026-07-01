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
                true,
                true,
                GenerationRequestStatus.RECEIVED,
                OffsetDateTime.parse("2026-03-08T10:15:30Z")
        );
        GenerationRequest olderMatch = persist(
                "billing-service",
                true,
                true,
                GenerationRequestStatus.RECEIVED,
                OffsetDateTime.parse("2026-03-07T10:15:30Z")
        );
        persist(
                "analytics-service",
                false,
                true,
                GenerationRequestStatus.RECEIVED,
                OffsetDateTime.parse("2026-03-09T10:15:30Z")
        );
        persist(
                "invoice-service",
                true,
                true,
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
                true,
                false,
                GenerationRequestStatus.RECEIVED,
                OffsetDateTime.parse("2026-03-06T10:15:30Z")
        );
        GenerationRequest newest = persist(
                "payments-service",
                true,
                true,
                GenerationRequestStatus.GENERATING,
                OffsetDateTime.parse("2026-03-08T10:15:30Z")
        );

        List<GenerationRequest> results = repositoryAdapter.findAllByFilters(GenerationRequestFilters.empty());

        assertThat(results)
                .extracting(GenerationRequest::id)
                .containsSequence(newest.id(), oldest.id());
    }

    @Test
    void shouldDeleteGenerationRequestById() {
        GenerationRequest request = persist(
                "payments-service",
                true,
                true,
                GenerationRequestStatus.RECEIVED,
                OffsetDateTime.parse("2026-03-08T10:15:30Z")
        );

        boolean deleted = repositoryAdapter.deleteById(request.id());

        assertThat(deleted).isTrue();
        assertThat(jpaRepository.findById(request.id())).isEmpty();
    }

    @Test
    void shouldReturnFalseWhenDeletingMissingGenerationRequest() {
        boolean deleted = repositoryAdapter.deleteById(UUID.randomUUID());

        assertThat(deleted).isFalse();
    }

    @Test
    void shouldPersistGenerationStatusMetadata() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-03-08T10:15:30Z");
        GenerationRequest request = new GenerationRequest(
                UUID.randomUUID(),
                "catalog-service",
                "spring-boot-hexagonal",
                true,
                true,
                false,
                false,
                DeploymentTarget.KUBERNETES,
                GenerationRequestStatus.GENERATED,
                "{\"name\":\"catalog-service\"}",
                "Generation completed",
                "s3://artifacts/catalog.zip",
                "registry/catalog:latest",
                createdAt,
                createdAt.plusMinutes(2)
        );

        GenerationRequest saved = repositoryAdapter.save(request);
        GenerationRequest reloaded = repositoryAdapter.findById(saved.id()).orElseThrow();

        assertThat(reloaded.status()).isEqualTo(GenerationRequestStatus.GENERATED);
        assertThat(reloaded.message()).isEqualTo("Generation completed");
        assertThat(reloaded.artifactRef()).isEqualTo("s3://artifacts/catalog.zip");
        assertThat(reloaded.imageRef()).isEqualTo("registry/catalog:latest");
    }

    private GenerationRequest persist(
            String name,
            boolean database,
            boolean security,
            GenerationRequestStatus status,
            OffsetDateTime createdAt
    ) {
        GenerationRequest generationRequest = new GenerationRequest(
                UUID.randomUUID(),
                name,
                "spring-boot-hexagonal",
                database,
                true,
                security,
                false,
                DeploymentTarget.KUBERNETES,
                status,
                "{\"name\":\"" + name + "\"}",
                null,
                null,
                null,
                createdAt,
                createdAt
        );
        return persistenceMapper.toDomain(jpaRepository.save(persistenceMapper.toEntity(generationRequest)));
    }
}
